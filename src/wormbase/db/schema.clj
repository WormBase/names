(ns wormbase.db.schema
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [cognitect.transcriptor :as xr]
   [datomic.api :as d]
   [io.rkn.conformity :as c]
   [wormbase.names.entity :as wne]
   [wormbase.util :refer [read-edn datomic-internal-namespaces]])
  (:import (java.io PushbackReader)))

(defn definitions [db]
  (d/q '[:find [?attr ...]
         :in $ ?excludes
         :where
         [?e :db/valueType]
         [?e :db/ident ?attr]
         [(namespace ?attr) ?ns]
         (not
          [(contains? ?excludes ?ns)])]
       db
       (datomic-internal-namespaces)))

(defn write-edn [conn & {:keys [out-path]
                         :or {out-path "/tmp/schema.edn"}}]
  (binding [*out* (-> out-path io/file io/writer)]
    (let [qr (-> conn d/db definitions)
          se (->> qr
                  (sort-by namespace)
                  (map (comp d/touch #(d/entity (d/db conn) %)))
                  (map #(into {} %))
                  (map #(dissoc % :db/id))
                  vec)]
      (prn se))))

(defn apply-updates! []
  (doseq [rf (xr/repl-files "resources/schema/updates")]
    (xr/run rf)))

(defn import-people [conn]
  (let [people (read-edn (io/resource "schema/wbpeople.edn"))]
    (c/ensure-conforms conn
                       :import/people
                       {:people {:txes [people]}}
                       [:people])))

(defn install [conn]
  (let [db-fns (read-edn (io/resource "schema/tx-fns.edn"))
        schema-txes (read-edn (io/resource "schema/definitions.edn"))
        seed-data (read-edn (io/resource "schema/seed-data.edn"))
        entity-registry (read-edn (io/resource "schema/generic-entity-registry.edn"))
        init-schema [(concat db-fns schema-txes)]]
    (c/ensure-conforms conn {:initial-schema {:txes init-schema}})
    (c/ensure-conforms conn {:seed-data {:txes [seed-data]}})
    (doseq [{:keys [:entity-type :id-format :generic?]} entity-registry]
      (wne/register-entity-schema conn
                                  entity-type
                                  id-format
                                  {:provenance/why "initial system registration"}
                                  generic?
                                  true))
    (import-people conn)))
