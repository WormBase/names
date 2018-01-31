(ns org.wormbase.test-utils
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.test :as t]   
   [compojure.api.routes :as routes]
   [java-time :as jt]
   [muuntaja.core :as muuntaja]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.db :as owdb]
   [peridot.core :as p]
   [spec-tools.core :as stc])
  (:import (java.io InputStream)))

;; TODO: Unify way of creating muuntaja formats "instance"?
;;       (Duplication as per o.w.n/service.clj - which does it correctly!)
;;       - usage here is ok, is just for testing.

(def mformats (muuntaja/create))

(defn read-body [body]
  (if (instance? InputStream body)
    (slurp body)
    body))

(defn parse-body [body]
  (let [body (read-body body)
        body (if (instance? String body)
               (try
                 (muuntaja/decode mformats "application/edn" body)
                 (catch clojure.lang.ExceptionInfo jpe
                   (let [divider #(format "-----------------: % :-----------------" %)]
                     (println (divider "DEBUGING"))
                     (println "Error type:" (type jpe))
                     (println "Cause:" (.getCause jpe))
                     (println "Invalid response data format? body was returned as:"
                              (type body))
                     (println (if (or (nil? body) (str/blank? body))
                                (println "BODY WAS NIL or empty string!")
                                body))
                     (println (divider "END DEBUGGING"))
                     (println))
                   (throw jpe)))
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
      (println "********** COULD NOT ENCODE " x "as EDN")
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

(defn raw-put-or-post* [app uri method data content-type headers]
  (let [request (-> (p/session app)
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

;;
;; ring-request
;;

(defn ring-request [m format data]
  {:uri "/echo"
   :request-method :post
   :body (muuntaja/encode mformats format data)
   :headers {"content-type" format
             "accept" format}})

;;
;; get-spec
;;

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

(defn- map->set [m]
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
  (let [biotype (-> sample :gene/biotype :biotype/id)
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
                     & {:keys [user why when]
                        :or {user [:user/email "tester@wormbase.org"]
                             when (jt/to-java-date (jt/instant))}}]
  (let [conn (db-testing/fixture-conn)
        data (sample-to-txes data-samples)
        prov (merge {:db/id "datomic.tx"
                     :provenance/when when
                     :provenance/who user}
                    (if why
                      {:provenance/why why}))
        tx-fixtures [data prov]]
    (with-redefs [owdb/db (fn speculative-db [_]
                            (db-testing/speculate tx-fixtures))]
      ;; (prn "FIXTURES:")
      ;; (prn tx-fixtures)
      (test-fn data prov))))
