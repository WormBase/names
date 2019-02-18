(ns wormbase.names.importers.processing
  (:require
   [clojure.data.csv :as csv]   
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [datomic.api :as d]
   [java-time :as jt]
   [semantic-csv.core :as sc]))

(defn- throw-parse-exc! [spec value]
  (throw (ex-info "Could not parse"
                  {:spec spec
                   :value value
                   :problems (s/explain-data spec value)})))


(defn discard-empty-valued-entries [data]
  (reduce-kv (fn [m k v]
               (if (nil? v)
                 (dissoc m k)
                 m))
             data
             data))

(defn conformed [spec value & {:keys [transform]
                               :or {transform identity}}]
  (cond
    (str/blank? value) nil
    (s/valid? spec value) (transform (s/conform spec value))
    :else (throw-parse-exc! spec value)))

(defn conformed-ref [spec value]
  [spec (conformed spec value)])

(defn conformed-label [or-spec value]
  (conformed or-spec value :transform first))

(defn handle-cast-exc [& xs]
  (throw (ex-info "Error!" {:x xs})))

(defn parse-tsv [stream]
  (csv/read-csv stream :separator \tab))

(defn parse-transform-cast [in-file conf cast-fns]
  (->> (parse-tsv in-file)
       (sc/mappify (select-keys conf [:header]))
       (sc/cast-with cast-fns {:exception-handler handle-cast-exc})))

(defn transact-batch [event-type conn tx-batch]
  (let [tx-data (conj tx-batch {:db/id "datomic.tx"
                                :provenance/what event-type
                                :provenance/how :agent/importer})]
    (d/transact-async conn tx-data)))

(defn ->when
  "Convert the provenance timestamp into a time-zone aware datetime."
  [value & {:keys [tz] :or {tz "UTC"}}]
  (-> (jt/local-date-time value)
      (jt/zoned-date-time tz)
      (jt/with-zone-same-instant tz)
      (jt/instant)
      (jt/to-java-date)))
