(ns wormbase.db.schema
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [datomic.api :as d]
   [io.rkn.conformity :as c]
   [wormbase.util :refer [read-edn]])
  (:import (java.io PushbackReader)))

(defn definitions [db]
  (d/q '[:find [?attr ...]
         :in $ [?include-ns ...]
         :where
         [?e :db/valueType]
         [?e :db/ident ?attr]
         [(namespace ?attr) ?ns]
         [(= ?ns ?include-ns)]]
       db
       #{"agent"
         "biotype"
         "event"
         "gene"
         "gene.status"
         "provenance"
         "species"
         "template"
         "person"}))

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


;; TODO: conformity uses `schema-ident` to uniquely identity idempotent
;;       schema transactions.
;;       find a way to version the schema by passing this in.
;;       e.g: could be release number (WS\d+), or a timestamp-ed string
(defn install [conn run-n]
  (let [schema-ident (keyword (str "schema-" run-n))]
    (let [db-fns (read-edn (io/resource "schema/tx-fns.edn"))
          schema-txes (read-edn (io/resource "schema/definitions.edn"))
          seed-data (read-edn (io/resource "schema/seed-data.edn"))
          people (read-edn (io/resource "schema/wbpeople.edn"))
          init-schema [(concat db-fns schema-txes)]]
      ;; NOTE: datomic-schema-grapher.core/graph-datomic won't show the
      ;;       relations without some data installed.
      ;;       i.e schema alone will not draw the  between refs.
      ;;           (arrows on digagrea)
      (c/ensure-conforms conn {:initial-schema {:txes init-schema}})
      (c/ensure-conforms conn {:seed-data {:txes [seed-data]}})
      (c/ensure-conforms conn {:people {:txes [people]}}))))

