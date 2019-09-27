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
   [expound.alpha :refer [expound-str]]
   [java-time :as jt]
   [miner.strgen :as sg]
   [muuntaja.core :as muuntaja]
   [peridot.core :as p]
   [wormbase.db :as wdb]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.gen-specs.person :as gsp]
   [wormbase.gen-specs.species :as gss]
   [wormbase.names.entity :as wne]
   [wormbase.names.gene :as wng]
   [wormbase.names.response-formats :as wnrf]
   [wormbase.names.service :as wns]
   [wormbase.names.util :as wnu]
   [wormbase.specs.gene :as wsg]
   [wormbase.specs.species :as wss])
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
        body* (if (instance? String body)
                (try
                  (muuntaja/decode mformats "application/json" body)
                  (catch ExceptionInfo exc
                    (log-decode-err exc body)
                    (throw exc)))
                body)]
    body*))

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

(defn delete [app uri content-type & [data headers]]
  (let [payload (.getBytes data)
        request (-> app
                    p/session
                    (p/request uri
                               :request-method :delete
                               :headers (or headers {})
                               :content-type (or content-type
                                                 "application/edn")
                               :body payload))
        {{:keys [status body response-headers]} :response} request]
    [status (parse-body body) response-headers]))

(defn raw-put-or-post* [app uri method data content-type headers]
  (let [payload (.getBytes data)
        request (-> app
                    p/session
                    (p/request uri
                               :request-method method
                               :headers (or headers {})
                               :content-type (or content-type
                                                 "application/edn")
                               :body payload))
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
                                        "/"
                                        (str "/" (name k)))
                                      v]))))
      spec)))

(defn species->latin-name [lu-ref]
  (let [[ident value] lu-ref
        res (if (= ident :species/latin-name)
              value
              (->> (gss/load-seed-data)
                   (filter #(= (ident %) value))
                   (first)
                   :species/latin-name))]
    res))

(defn species->ref
  "Updates the value corresponding to `:gene/species` to be a lookup reference. "
  [data]
  (let [species-value (:gene/species data)]
    (cond
      (keyword? species-value) (update data
                                       :gene/species
                                       (fn [sv]
                                         [:species/latin-name
                                          (species->latin-name [:species/id sv])]))
      (vector? species-value) data
      :otherwise (update data
                         :gene/species
                         (fn use-latin-name [sname]
                           (let [lur (s/conform ::wss/identifier sname)]
                             [:species/latin-name (species->latin-name lur)]))))))

(defn species-ref->latin-name
  "Retrive the name of the gene species from a mapping."
  [data]
  (-> data :gene/species second))

(defn gene-sample-to-txes
  "Convert a sample generated from a spec into a transactable form."
  [sample]
  (let [biotype (:gene/biotype sample)
        species (:gene/species sample)
        species* (cond (keyword? species)
                       [:species/latin-name (species->latin-name [:species/id species])]
                       (string? species)
                       [:species/latin-name species]
                       :else species)
        assoc-if (fn [m k v]
                   (if v
                     (assoc m k v)
                     m))]
    (-> sample
        (dissoc :provenance/who)
        (dissoc :provenance/why)
        (dissoc :provenance/when)
        (dissoc :provenance/how)
        (dissoc :gene/biotype)
        (assoc-if :gene/species species*)
        (assoc-if :gene/biotype biotype))))

(defn provenance
  [data & {:keys [how what whence why person status batch-id]
           :or {how :agent/console
                whence (jt/to-java-date (jt/instant))
                what :event/test-fixture-assertion
                person [:person/email "tester@wormbase.org"]}}]
  (merge {:db/id "datomic.tx"
          :provenance/how how
          :provenance/what what
          :provenance/when whence
          :provenance/who person}
         (when why
           {:provenance/why why})
         (when batch-id
           {:batch/id batch-id})))

(defn with-fixtures
  ([data-samples test-fn]
   (with-fixtures nil data-samples test-fn))
  ([connection data-samples test-fn]
   (with-fixtures identity provenance connection data-samples test-fn))
  ([sample-transform provenance-fn connection data-samples test-fn]
   (let [conn (if connection
                connection
                (db-testing/fixture-conn))
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
       (test-fn conn)))))

(defn with-installed-generic-entity
  ([entity-type id-template provenance-fn fixtures test-fn]
   (let [conn (db-testing/fixture-conn)]
     (wne/register-entity-schema conn entity-type id-template (provenance-fn {}))
     (with-fixtures conn (or fixtures []) test-fn)))
  ([entity-type id-template fixtures test-fn]
   (with-installed-generic-entity entity-type id-template provenance fixtures test-fn))
  ([entity-type id-template test-fn]
   (with-installed-generic-entity entity-type id-template provenance nil test-fn)))

(defn with-batch-fixtures
  [sample-transform provenance-fn data-samples test-fn]
  (let [conn (db-testing/fixture-conn)
        sample-data (if (map? data-samples)
                      [data-samples]
                      data-samples)
        data (map sample-transform sample-data)
        prov (when (ifn? provenance-fn)
               (provenance-fn data))
        tx-fixtures (if prov
                      (conj data prov)
                      data)]
    @(d/transact-async conn tx-fixtures)
    (with-redefs [wdb/connection (constantly conn)
                  wdb/db (fn [_] (d/db conn))]
      (test-fn conn))))

(defn with-variation-fixtures [fixtures test-fn]
  (with-installed-generic-entity :variation/id "WBVar%08d" fixtures test-fn))

(def with-gene-fixtures (partial with-fixtures gene-sample-to-txes provenance nil))

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

(defn gen-valid-name-for-sample [sample generator]
  (let [select-species-name (fn [v]
                              (if (vector? v)
                                (second v)
                                v))]
    (-> sample
        :species
        select-species-name
        generator
        (gen/sample 1)
        first)))

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

(defn gen-sample [data-gen n]
  (->> (gen/sample data-gen n)
       (map #(remove (comp nil? val) %))
       (map #(into {} %))
       (map #(dissoc % :history))))

(defn transform-ident-ref-values
  [m]
  (wne/transform-ident-ref-values m
                                  :skip-keys (if (-> m :gene/status qualified-keyword?)
                                               #{:gene/status}
                                               #{})))

(defn gene-samples [n]
  (assert (int? n))
  (let [gene-refs (into {}
                        (keep-indexed (fn [idx sample-id]
                                        [idx {:gene/id sample-id}])
                                      (gen/sample gsg/id n)))
        gene-recs (->> n
                       (gen-sample gsg/payload)
                       (map (fn make-names-valid [gr]
                              (assoc gr
                                     :sequence-name (seq-name-for-sample gr)
                                     :cgc-name (cgc-name-for-sample gr))))
                       (map #(wnu/qualify-keys % "gene"))
                       (map species->ref)
                       (map transform-ident-ref-values))
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

(def ->json wnrf/encode-content)

(defn query-gene-batch [db bid]
  (wnu/query-batch db bid wng/summary-pull-expr))
