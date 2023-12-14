(ns wormbase.names.importers.processing
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [datomic.api :as d]
   [environ.core :as environ]
   [java-time.api :as jt]
   [semantic-csv.core :as sc]
   [wormbase.names.auth :as wna]
   [wormbase.specs.entity :as wse]
   [wormbase.util :as wu]))

(defn- throw-parse-exc! [spec value]
  (throw (ex-info "Could not parse"
                  {:spec spec
                   :value value
                   :problems (s/explain-data spec value)})))


(defn conformed [spec value & {:keys [transform]
                               :or {transform identity}}]
  (if (s/valid? spec value)
    (transform (s/conform spec value))
    (throw-parse-exc! spec value)))

(defn conformed-ref [spec value]
  [spec (conformed spec value)])

(defn conformed-label [or-spec value]
  (conformed or-spec value :transform first))

(defn handle-cast-exc [& xs]
  (throw (ex-info "Error!" {:x xs})))

(defn ->status
  [ent-ns status]
  (keyword (str ent-ns ".status") (str/lower-case status)))

(defn parse-tsv [stream]
  (csv/read-csv stream :separator \tab))

(defn transform-cast [conf cast-fns rows]
  (->> rows
       (sc/mappify (select-keys conf [:header]))
       (sc/cast-with cast-fns {:exception-handler handle-cast-exc})))

(defn read-data [tsv-path conf _ cast-fns]
  (with-open [in-file (io/reader tsv-path)]
    (->> (parse-tsv in-file)
         (transform-cast conf cast-fns)
         (map wu/discard-empty-valued-entries)
         (doall))))

(defn handle-transact-exc [exc data]
  (throw (ex-info "Failed to transact data!"
                  {:data data
                   :orig-exc exc})))

(defn noisy-transact [conn data]
  (try
    (d/transact-async conn data)
    (catch java.util.concurrent.ExecutionException exc
      (handle-transact-exc exc data))
    (catch java.lang.IllegalArgumentException exc
      (handle-transact-exc exc data))
    (catch Exception exc
      (handle-transact-exc exc data))))

(defn transact-batch
  [person-lur event-type conn tx-batch & {:keys [transact-fn]
                                          :or {transact-fn noisy-transact}}]
  (let [tx-data (conj tx-batch {:db/id "datomic.tx"
                                :provenance/what event-type
                                :provenance/who person-lur
                                :provenance/how :agent/importer})]
    (transact-fn conn tx-data)))

(defn make-dead-predicate
  [ent-ns equality-pred-fn]
  (let [status-ident (keyword ent-ns "status")
        status-ident-val (keyword (str ent-ns ".status") "dead")]
    (fn [x]
      (equality-pred-fn (status-ident x) status-ident-val))))

(defn fixup-non-live-entity
  [db name-idents entity]
  (reduce-kv (fn [m k v]
               (if (and ((set name-idents) k)
                        (d/entity db (find m k)))
                 (dissoc m k)
                 (assoc m k v)))
             entity
             entity))

(defn transact-entities
  "Transact a batch of entities in two stages, attempt to preserve names.

  Names in the system should be unique; names in the sources data are sometiems duplicated
  and remain on dead entities.

  Transacts all non-dead entities first, then all dead entities one-by-one,
  which is required, in order to retain names still attached to dead entities.

  :parameters
   - conn : datomic connnection
   - data : the data to transact
   - ent-ns : The namespace (string) of the entity type of the batch being transacted.
   - person-lur : Datomic lookup-ref of the person performing the import.
   - batcher : function accepting tsv-path, conf and a filter fn to generate batches.
  "
  [conn data ent-ns person-lur batcher name-idents]
  (let [not-dead? (make-dead-predicate ent-ns not=)
        dead? (make-dead-predicate ent-ns =)]
    (doseq [not-dead-batch (batcher data ent-ns not-dead?)]
      @(transact-batch person-lur :event/import conn not-dead-batch))
    (doseq [dead-batch (batcher data ent-ns dead? :batch-size 1)]
      @(transact-batch person-lur
                       :event/import
                       conn
                       (map (partial fixup-non-live-entity (d/db conn) name-idents)
                            dead-batch)))))

(defn ->when
  "Convert the provenance timestamp into a time-zone aware datetime."
  [value & {:keys [tz] :or {tz "UTC"}}]
  (-> (jt/local-date-time value)
      (jt/zoned-date-time tz)
      (jt/with-zone-same-instant tz)
      (jt/instant)
      (jt/to-java-date)))

(defn check-environ! []
  (when-not (environ/env :token)
    (throw (ex-info "Auth information not set - please set the TOKEN environment variable."
                    {}))))

(defn default-who
  "Return a datomic lookup ref for a person from the ID token supplied in the environment."
  []
  (some-> (environ/env :token)
          (wna/parse-token)
          (select-keys [:email])
          (set/rename-keys {:email :person/email})
          (find :person/email)))

(defn ->who [value]
  (if value
    (conformed-ref :person/id value)
    (default-who)))
