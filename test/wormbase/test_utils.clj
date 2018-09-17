(ns wormbase.test-utils
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as str]
   [clojure.test :as t]
   [clojure.tools.logging :as log]
   [compojure.api.routes :as routes]
   [datomic.api :as d]
   [java-time :as jt]
   [miner.strgen :as sg]
   [muuntaja.core :as muuntaja]
   [wormbase.db-testing :as db-testing]
   [wormbase.db :as wdb]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.gen-specs.person :as gsp]
   [wormbase.gen-specs.species :as gss]
   [wormbase.names.service :as wns]
   [wormbase.specs.gene :as wsg]
   [peridot.core :as p]
   [spec-tools.core :as stc])
  (:import
   (clojure.lang ExceptionInfo)
   (java.io InputStream)))

(def mformats (muuntaja/create))

(defn read-body [body]
  (if (instance? InputStream body)
    (slurp body)
    body))

(defn- log-decode-err [^Exception exc body]
  (let [divider #(format "-----------------: % :-----------------" %)]
    (log/debug "Error type:" (type exc))
    (log/debug "Cause:" (.getCause exc))
    (log/debug "Invalid response data format? body was returned as:"
               (type body))
    (log/debug (if (or (nil? body) (str/blank? body))
                 "BODY WAS NIL or empty string!"
                 body))))

(defn parse-body [body]
  (let [body (read-body body)
        body (if (instance? String body)
               (try
                 (muuntaja/decode mformats "application/json" body)
                 (catch ExceptionInfo exc
                   (log-decode-err exc body)
                   (throw exc)))
               body)]
    body))

(defn extract-schema-name [ref-str]
  (last (str/split ref-str #"/")))

(defn find-definition [spec ref]
  (let [schema-name (keyword (extract-schema-name ref))]
    (get-in spec [:definitions schema-name])))

(defn edn-stream [x]
  (muuntaja/encode mformats "application/edn" x))

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
  (let [[status body headers] (raw-get* app uri params headers)]
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

(defmacro status-is? [status expected-status body]
  `(t/is (= ~status ~expected-status)
         (format "Response body did not contain expected data:\n%s"
                 (pr-str (if-let [suspec# (:spec ~body)]
                           (stc/deserialize suspec#)
                           ~body)))))

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

(defn gene-sample-to-txes
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

(defn with-fixtures
  [sample-transform provenance-fn data-samples test-fn]
  (let [conn (db-testing/fixture-conn)
        sample-data (if (map? data-samples)
                      [data-samples]
                      data-samples)]
    (doseq [data-sample sample-data]
      (let [data (sample-transform data-sample)
            prov (when (ifn? provenance-fn)
                   (provenance-fn data))
            tx-fixtures (if prov
                          (conj [data] prov)
                          [data])]
        @(d/transact-async conn tx-fixtures)))
    (with-redefs [wdb/connection (fn [] conn)
                  wdb/db (fn speculate [_]
                           (d/db conn))]
      (test-fn conn))))

(defn gene-provenance
  [data & {:keys [how what whence why person status]
           :or {how :agent/console
                whence (jt/to-java-date (jt/instant))
                what :event/test-fixture-assertion
                person [:person/email "tester@wormbase.org"]}}]
  (merge {:db/id "datomic.tx"
          :provenance/how how
          :provenance/when whence
          :provenance/who person}
         (when-not (:gene/status data)
           {:gene/status :gene.status/live})
         (when why
           {:provenance/why why})))

(def with-gene-fixtures (partial with-fixtures
                                 gene-sample-to-txes
                                 gene-provenance))

(def with-person-fixtures (partial with-fixtures
                                   identity
                                   nil))

(defn query-provenance [conn gene-id event]
  (when-let [tx-ids (d/q '[:find [?tx]
                           :in $ ?lur ?event
                           :where
                           [?ev :db/ident ?event]
                           [?tx :provenance/what ?ev]
                           [?lur :gene/id _ ?tx]]
                         (-> conn d/db d/history)
                         [:gene/id gene-id]
                         event)]
    (map #(d/pull (d/db conn)
                  '[*
                    {:provenance/how [:db/ident]
                     :provenance/what [:db/ident]
                     :provenance/who [:person/email]}]
                  %)
         tx-ids)))

(defn- gen-valid-name-for-sample [sample generator]
  (-> sample
      :gene/species
      :species/id
      generator
      (gen/sample 1)
      first))

(defn cgc-name-for-sample [sample]
  (gen-valid-name-for-sample sample gss/cgc-name))

(defn seq-name-for-sample [sample]
  (gen-valid-name-for-sample sample gss/sequence-name))

(defn gen-valid-name-for-species [gen-fn species]
  (-> (gen-fn species)
      (gen/sample 1)
      (first)))

(def seq-name-for-species (partial gen-valid-name-for-species
                                   gss/sequence-name))

(def cgc-name-for-species (partial gen-valid-name-for-species
                                   gss/cgc-name))

(defn uniq-names? [names]
  (let [nc (count names)]
    (or (= nc 1)
        (= nc (-> names set count)))))

(defn dup-names? [data-samples]
  (let [cgc-names (map :gene/cgc-name data-samples)
        seq-names (map :gene/sequence-name data-samples)]
    (not (and (uniq-names? cgc-names)
              (uniq-names? seq-names)))))

(defn gene-samples [n]
  (assert (int? n))
  (let [gene-refs (into {}
                        (keep-indexed (fn [idx sample-id]
                                        [idx {:gene/id sample-id}])
                                      (gen/sample gsg/id n)))
        gene-recs (map (fn make-valid [m]
                         (-> m
                             (dissoc :history)
                             (assoc :gene/cgc-name (cgc-name-for-sample m)
                                    :gene/sequence-name (seq-name-for-sample m))))
                       (gen/sample gsg/payload n))
        data-samples (keep-indexed
                      (fn [i gr]
                        (merge (get gene-refs i) gr)) gene-recs)
        gene-ids (map :gene/id (-> gene-refs vals flatten))]
    (let [dn (dup-names? data-samples)]
      (if dn
        (recur n)
        data-samples))))

(defn person-samples [n]
  (let [data-samples (gen/sample gsp/person n)]
    data-samples))

(defn ->json [data]
  (let [formats (deref #'wns/mformats)]
    (-> formats
        (muuntaja/encode "application/json" data)
        (slurp))))

