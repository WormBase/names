(ns wormbase.names.importers.entity
  (:require
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [datomic.api :as d]
   [environ.core :refer [env]]
   [mount.core :as mount]
   [wormbase.names.entity :as wne]
   [wormbase.names.importers.processing :as wnip]
   [wormbase.specs.entity :as wse]
   [wormbase.util :as wu]))

(defn ->status
  [status]
  (assert (not (str/blank? status)) "Blank status!")
  (keyword "variation.status" (str/lower-case status)))

(defn make-export-conf
  [ent-ns named?]
  (let [attrs (conj ["id" "status"] (if named? "name"))]
    {:header (map (partial keyword ent-ns) attrs)}))

(defn build-data-txes
  "Build the current entity representation of each variation."
  [tsv-path conf ent-ns & {:keys [batch-size]
                           :or {batch-size 500}}]
  (let [cast-fns (select-keys
                  {(keyword ent-ns "id") (partial wnip/conformed ::wse/id)
                   (keyword ent-ns "name") (partial wnip/conformed ::wse/name)
                   (keyword ent-ns "status") ->status}
                  (-> conf :header))]
    (with-open [in-file (io/reader tsv-path)]
      (->> (wnip/parse-tsv in-file)
           (wnip/transform-cast conf cast-fns)
           (map wnip/discard-empty-valued-entries)
           (partition-all batch-size)
           (doall)))))

(defn batch-transact-data
  [conn ent-ns id-template tsv-path person-lur]
  (let [id-ident (keyword ent-ns "id")
        prov {:db/id "datomic.tx"
              :provenance/what :event/import
              :provenance/who person-lur
              :provenance/when (wu/now)
              :provenance/how :agent/importer}
        reg-prov (assoc prov :provenance/what :event/new-entity-type)
        ent-named? (-> (wnip/parse-tsv tsv-path)
                       (first)
                       (count)
                       (boolean)) ;; number of cols n file determines if named or not.
        conf (make-export-conf ent-ns ent-named?)
        name-required? (boolean (some #(= (name %) "name") (keys conf)))]
    (wne/register-entity-schema conn
                                id-ident
                                id-template
                                reg-prov
                                true
                                true
                                name-required?)
    (doseq [batch (build-data-txes tsv-path conf ent-ns)]
      @(wnip/transact-batch person-lur
                            :event/import
                            conn
                            (conj batch (assoc prov :batch/id (d/squuid)))))))

(defn process
  ([conn ent-ns id-template data-tsv-path]
   (process conn ent-ns id-template data-tsv-path 10))
  ([conn ent-ns id-template data-tsv-path n-in-flight]
   (batch-transact-data conn ent-ns id-template data-tsv-path (wnip/default-who))))
