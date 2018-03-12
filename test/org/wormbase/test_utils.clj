(ns org.wormbase.test-utils
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as str]
   [clojure.test :as t]
   ;; TODO
   ;; [clojure.tools.logging :as log]
   [compojure.api.routes :as routes]
   [datomic.api :as d]
   [java-time :as jt]
   [miner.strgen :as sg]
   [muuntaja.core :as muuntaja]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.db :as owdb]
   [org.wormbase.specs.gene :as owsg]
   [peridot.core :as p]
   [spec-tools.core :as stc])
  (:import
   (clojure.lang ExceptionInfo)
   (java.io InputStream)))

;; TODO: Unify way of creating muuntaja formats "instance"?
;;       (Duplication as per o.w.n/service.clj - which does it correctly!)
;;       - usage here is ok, is just for testing.

(def mformats (muuntaja/create))

(defn read-body [body]
  (if (instance? InputStream body)
    (slurp body)
    body))

(defn- print-decode-err [^Exception exc body]
  (let [divider #(format "-----------------: % :-----------------" %)]
    (println (divider "DEBUGING"))
    (println "Error type:" (type exc))
    (println "Cause:" (.getCause exc))
    (println "Invalid response data format? body was returned as:"
             (type body))
    (println (if (or (nil? body) (str/blank? body))
               (println "BODY WAS NIL or empty string!")
               body))
    (println (divider "END DEBUGGING"))
    (println)))

(defn parse-body [body]
  (let [body (read-body body)
        body (if (instance? String body)
               (try
                 (muuntaja/decode mformats "application/edn" body)
                 (catch ExceptionInfo exc
                   (print-decode-err exc body)
                   (throw exc)))
               body)]
    body))

(defn extract-schema-name [ref-str]
  (last (str/split ref-str #"/")))

(defn find-definition [spec ref]
  (let [schema-name (keyword (extract-schema-name ref))]
    (get-in spec [:definitions schema-name])))

(defn edn-stream [x]
  (try
    (muuntaja/encode mformats "application/edn" x)
    (catch Exception e
      (println  "********** COULD NOT ENCODE " x "as EDN")
      (throw e))))

(defn follow-redirect [state]
  (if (some-> state :response :headers (get "Location"))
    (p/follow-redirect state)
    state))

(def ^:dynamic *async?*
  (= "true" (System/getProperty "compojure-api.test.async")))

(defn- call-async [handler request]
  (let [result (promise)]
    (handler request #(result [:ok %]) #(result [:fail %]))
    (if-let [[status value] (deref result 1500 nil)]
      (if (= status :ok)
        value
        (throw value))
      (throw
       (Exception. (str "Timeout while waiting for the request handler. "
                        request))))))

(defn call
  "Call handler synchronously or asynchronously depending on *async?*."
  [handler request]
  (if *async?*
    (call-async handler request)
    (handler request)))

(defn raw-get* [app uri & [params headers]]
  (let [{{:keys [status body headers]} :response}
        (-> (cond->> app *async?* (partial call-async))
            (p/session)
            (p/request uri
                       :request-method :get
                       :params (or params {})
                       :headers (or headers {}))
            follow-redirect)]
    [status (read-body body) headers]))

(defn get* [app uri & [params headers]]
  (let [[status body headers]
        (raw-get* app uri params headers)]
    [status (parse-body body) headers]))

(defn form-post* [app uri params]
  (let [{{:keys [status body]} :response}
        (-> app
            (p/session)
            (p/request uri
                       :request-method :post
                       :params params))]
    [status (parse-body body)]))

(defn delete [app uri content-type headers]
  (let [request (-> app
                    p/session
                    (p/request uri
                               :request-method :delete
                               :headers (or headers {})
                               :content-type (or content-type
                                                 "application/edn")))
        {{:keys [status body response-headers]} :response} request]
    [status (parse-body body) response-headers]))

(defn raw-put-or-post* [app uri method data content-type headers]
  (let [request (-> app
                    p/session
                    (p/request uri
                               :request-method method
                               :headers (or headers {})
                               :content-type (or content-type
                                                 "application/edn")
                               :body (.getBytes data)))
        {{:keys [status body response-headers]} :response} request]
    [status (read-body body) response-headers]))

(defn raw-post* [app uri & [data content-type headers]]
  (raw-put-or-post* app uri :post data content-type headers))

(defn post* [app uri & [data]]
  (let [[status body] (raw-post* app uri data)]
    [status (parse-body body)]))

(defn put* [app uri & [data]]
  (let [[status body] (raw-put-or-post* app uri :put data nil nil)]
    [status (parse-body body)]))

(defn headers-post* [app uri headers]
  (let [[status body] (raw-post* app uri "" nil headers)]
    [status (parse-body body)]))

(defn ring-request [m format data]
  {:uri "/echo"
   :request-method :post
   :body (muuntaja/encode mformats format data)
   :headers {"content-type" format
             "accept" format}})

(defn extract-paths [app]
  (-> app routes/get-routes routes/all-paths))

(defn get-spec [app]
  (let [[status spec] (get* app "/swagger.json" {})]
    (assert (= status 200))
    (if (:paths spec)
      (update-in spec [:paths] (fn [paths]
                                 (into
                                   (empty paths)
                                   (for [[k v] paths]
                                     [(if (= k (keyword "/"))
                                        "/" (str "/" (name k))) v]))))
      spec)))

(defn status-is? [status expected-status body]
  (t/is (= status expected-status)
        (format "Response body did not contain expected data:\n%s"
                (pr-str (if-let [suspec (:spec body)]
                          (stc/deserialize suspec)
                          body)))))

(defn map->set [m]
  (assert (map? m))
  (some->> (apply vector m)
           (flatten)
           (partition 2)
           (set)))

(defn body-contains? [body expected-data spec]
  (assert (map? expected-data) "expected-data must be a map")
  (let [exp-data (map->set expected-data)
        act-data (map->set body)]
    (t/is (set/subset? exp-data act-data)
          (str/join "\n" ["Expected data:"
                          (pr-str exp-data)
                          "not found in response body:"
                          (pr-str body)]))
    (when-let [rspec (:spec body)]
      (pprint (stc/deserialize rspec)))))

(defn sample-to-txes
  "Convert a sample generated from a spec into a transactable form."  
  [sample]
  (let [biotype (:gene/biotype sample)
        species (-> sample :gene/species vec first)
        assoc-if (fn [m k v]
                   (if v
                     (assoc m k v)
                     m))]
    (-> sample
        (dissoc :provenance/who)
        (dissoc :provenance/why)
        (dissoc :provenance/when)
        (dissoc :provenance/how)
        (dissoc :gene/species)
        (dissoc :gene/biotype)
        (assoc :gene/species species)
        (assoc-if :gene/biotype biotype))))

(defn with-fixtures [data-samples test-fn
                     & {:keys [how whence why person status]
                        :or {how :agent/console
                             whence (jt/to-java-date (jt/instant))
                             person [:person/email "tester@wormbase.org"]}}]
  (let [conn (db-testing/fixture-conn)
        sample-data (if (map? data-samples)
                      [data-samples]
                      data-samples)]
    (doseq [data-sample sample-data]
      (let [data (sample-to-txes data-sample)
            prov (merge {:db/id "datomic.tx"
                         :provenance/how how
                         :provenance/when whence
                         :provenance/who person}
                        (when-not (:gene/status data)
                          {:gene/status :gene.status/live})
                        (when why
                          {:provenance/why why}))
            tx-fixtures [data prov]]
        @(d/transact-async conn tx-fixtures)))
    (with-redefs [owdb/connection (fn [] conn)
                  owdb/db (fn speculative-db [_]
                            (d/db conn))]
      (test-fn conn))))

(defn query-provenance [conn gene-id]
  (some->> (d/q '[:find [?tx ?who ?when ?why ?how]
                  :in $ ?gene-id
                  :where
                  [?gene-id :gene/id _ ?tx]
                  [?tx :provenance/who ?u-id]
                  [(get-else $ ?u-id :person/email "nobody") ?who]
                  [(get-else $ ?tx :provenance/when :unset) ?when]
                  [(get-else $ ?tx :provenance/why "Dunno") ?why]
                  [(get-else $ ?tx :provenance/how :unset) ?how-id]
                  [?how-id :db/ident ?how]]
                (-> conn d/db d/history)
                [:gene/id gene-id])
           (zipmap [:tx-id
                    :provenance/who
                    :provenance/when
                    :provenance/why
                    :provenance/how])))

(defn gen-valid-name [name-kw species]
  (-> species
      owsg/name-patterns
      name-kw
      sg/string-generator
      (gen/sample 1)
      first))

(def gen-valid-seq-name (partial gen-valid-name :gene/sequence-name))

(def gen-valid-cgc-name (partial gen-valid-name :gene/cgc-name))

(defn gene-samples [n]
  (assert (int? n))
  (let [gene-refs (->> n
                       (gen/sample (s/gen :gene/id))
                       (map (partial array-map :gene/id)))
        gene-recs (gen/sample (s/gen ::owsg/update) n)
        data-samples
        (->> (interleave gene-refs gene-recs)
             (partition n)
             (map (partial apply merge))
             (map (fn correct-seq-name-for-species [m]
                    (let [sn (-> m
                                 :gene/species
                                 :species/id
                                 gen-valid-seq-name)]
                      (assoc m :gene/sequence-name sn)))))
        gene-ids (map :gene/id (flatten gene-refs))]
    (if-let [dup-seq-names? (->> data-samples
                                 (map :gene/sequence-name)
                                 (reduce =))]
      (recur n)
      [gene-ids gene-recs data-samples])))

