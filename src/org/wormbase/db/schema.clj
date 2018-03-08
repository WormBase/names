(ns org.wormbase.db.schema
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [datomic.api :as d]
   [io.rkn.conformity :as c]
   [org.wormbase.species :as ows]
   [org.wormbase.names.agent :as own-agent])
  (:import (java.io PushbackReader)))

;; TODO: not sure the canonical species listing should "live" here...
(def worms ["Caenorhabditis elegans"
            "Caenorhabditis briggsae"
            "Caenorhabditis remanei"
            "Caenorhabditis brenneri"
            "Pristionchus pacificus"
            "Caenorhabditis japonica"
            "Brugia malayi"
            "Onchocerca volvulus"
            "Strongyloides ratti"])

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

(defn read-edn [readable]
  (let [edn-read (partial edn/read {:readers *data-readers*})]
    (-> readable
        io/reader
        (PushbackReader.)
        (edn-read))))


;; TODO: conformity uses `schema-ident` to uniquely identity idempotent
;;       schema transactions.
;;       find a way to version the schema by passing this in.
;;       e.g: could be release number (WS\d+), or a timestamp-ed string
(defn install [conn run-n]
  (let [schema-ident (keyword (str "schema-" run-n))]
    (let [db-fns (read-edn (io/resource "schema/tx-fns.edn"))
          schema-txes (read-edn (io/resource "schema/definitions.edn"))
          seed-data (read-edn (io/resource "schema/seed-data.edn"))
          init-schema [(concat db-fns schema-txes)]]
      ;; NOTE: datomic-schema-grapher.core/graph-datomic won't show the
      ;;       relations without some data installed.
      ;;       i.e schema alone will not draw the arrows between refs.
      ;; (println txes)
      ;; (c/ensure-conforms conn {schema-ident {:txes db-fns}})
      (c/ensure-conforms conn {:initial-schema {:txes init-schema}})
      (c/ensure-conforms conn {:seed-data {:txes [seed-data]}})
      )))

