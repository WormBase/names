(ns wormbase.db.schema
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [cognitect.transcriptor :as xr]
   [datomic.api :as d]
   [io.rkn.conformity :as c]
   [wormbase.util :refer [read-edn]])
  (:import (java.io PushbackReader)))

(def datomic-internal-namespaces
  #{"db" "db.alter" "db.install" "db.excise" "db.sys" "conformity" "fressian"})

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
       datomic-internal-namespaces))

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

;; TODO: conformity uses `schema-ident` to uniquely identity idempotent
;;       schema transactions.
;;       find a way to version the schema by passing this in.
;;       e.g: could be release number (WS\d+), or a timestamp-ed string
(defn install [conn]
  (let [db-fns (read-edn (io/resource "schema/tx-fns.edn"))
        schema-txes (read-edn (io/resource "schema/definitions.edn"))
        seed-data (read-edn (io/resource "schema/seed-data.edn"))
        init-schema [(concat db-fns schema-txes)]]
    ;; NOTE: datomic-schema-grapher.core/graph-datomic won't show the
    ;;       relations without some data installed.
    ;;       i.e schema alone will not draw the  between refs.
    ;;           (arrows on digagrea)
    (c/ensure-conforms conn {:initial-schema {:txes init-schema}})
    (c/ensure-conforms conn {:seed-data {:txes [seed-data]}})
    (import-people conn)))
