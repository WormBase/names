(ns wormbase.db.schema
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [cognitect.transcriptor :as xr]
   [datomic.api :as d]
   [magnetcoop.stork :as stork]
   [wormbase.names.entity :as wne]
   [wormbase.util :as wu])
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
       (wu/datomic-internal-namespaces)))

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

(defn ensure-installed [conn resource-path]
  (->> resource-path
       (stork/read-resource)
       (stork/ensure-installed conn)
       (dorun)))

(defn apply-migrations
  [conn & {:keys [resource-path]
           :or {resource-path "schema/migrations"}}]
  (dorun (->> (wu/list-resource-files (str "resources/" resource-path))
              (map io/file)
              (map #(.getName %))
              (map #(ensure-installed conn (str resource-path "/" %))))))

(defn ensure-schema [conn]
  (ensure-installed conn "schema/definitions.edn")
  (ensure-installed conn "schema/seed-data.edn")
  (ensure-installed conn "schema/wbpeople.edn")
  (let [entity-registry (wu/read-edn (io/resource "schema/generic-entity-registry.edn"))]
    (doseq [{:keys [:entity-type :id-format :generic? :name-required?]} entity-registry]
      (wne/register-entity-schema conn
                                  entity-type
                                  id-format
                                  {:provenance/why "initial system registration"}
                                  generic?
                                  true
                                  name-required?)))
  (apply-migrations conn))
