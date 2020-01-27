(ns wormbase.names.importers.entity
  (:require
   [clojure.string :as str]
   [datomic.api :as d]
   [environ.core :refer [env]]
   [mount.core :as mount]
   [wormbase.names.entity :as wne]
   [wormbase.names.importers.processing :as wnip]
   [wormbase.specs.entity :as wse]
   [wormbase.util :as wu]))

(defn make-export-conf
  [ent-ns named?]
  (let [attrs (conj ["id" "status"] (if named? "name"))]
    {:data {:header (map (partial keyword ent-ns) attrs)}}))

(defn batch-data
  "Build the current entity representation of each variation."
  [prov data ent-ns filter-fn & {:keys [batch-size]
                                 :or {batch-size 500}}]
  (->> data
       (filter filter-fn)
       (partition-all batch-size)
       (map #(conj % (assoc prov :batch/id (d/squuid))))))

(defn batch-transact-data
  [conn ent-ns id-template tsv-path person-lur]
  (let [id-ident (keyword ent-ns "id")
        name-ident (keyword ent-ns "name")
        prov {:db/id "datomic.tx"
              :provenance/what :event/import
              :provenance/who person-lur
              :provenance/when (wu/now)
              :provenance/how :agent/importer}
        reg-prov (assoc prov :provenance/what :event/new-entity-type)
        ent-named? (-> (wnip/parse-tsv tsv-path)
                       (first)
                       (count)
                       (> 1)) ;; number of cols in file determines if named or not.
        conf (make-export-conf ent-ns ent-named?)
        headers (get-in conf [:data :header])
        name-required? (boolean (some #(= (name %) "name") headers))
        avail-cast-fns {(keyword ent-ns "id") (partial wnip/conformed ::wse/id)
                        name-ident (partial wnip/conformed ::wse/name)
                        (keyword ent-ns "status") (partial wnip/->status ent-ns)}
        cast-fns (select-keys avail-cast-fns headers)
        data (wnip/read-data tsv-path (:data conf) ent-ns cast-fns)]
    (wne/register-entity-schema conn
                                id-ident
                                id-template
                                reg-prov
                                true
                                true
                                name-required?)
    (wnip/transact-entities conn
                            data
                            ent-ns
                            person-lur
                            (partial batch-data prov)
                            [name-ident])))

(defn process
  ([conn ent-ns id-template data-tsv-path]
   (process conn ent-ns id-template data-tsv-path 10))
  ([conn ent-ns id-template data-tsv-path n-in-flight]
   (batch-transact-data conn ent-ns id-template data-tsv-path (wnip/default-who))))
