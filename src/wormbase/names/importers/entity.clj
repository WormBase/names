(ns wormbase.names.importers.entity
  (:require
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [datomic.api :as d]
   [environ.core :as environ]
   [wormbase.names.auth :as wna]
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
  (let [attrs (conj ["id" "status"] (if named? ["name"]))]
    {:header (map (partial keyword ent-ns) attrs)}))

(defn build-data-txes
  "Build the current entity representation of each variation."
  [tsv-path conf ent-ns & {:keys [batch-size]
                           :or {batch-size 500}}]
  (let [cast-fns (select-keys
                  {(keyword ent-ns "id") (partial wnip/conformed ::wse/id)
                   (keyword ent-ns "name") (partial wnip/conformed ::wse/name)
                   (keyword ent-ns "status") ->status}
                  (-> conf :data :header))]
    (with-open [in-file (io/reader tsv-path)]
      (->> (wnip/transform-cast (wnip/parse-tsv in-file) conf cast-fns)
           (map wnip/discard-empty-valued-entries)
           (partition-all batch-size)
           (doall)))))

(defn batch-transact-data
  [conn ent-ns id-template tsv-path]
  (let [event-ident (keyword "event" (str "import-" ent-ns))
        id-ident (keyword ent-ns "id")
        ;; form a WB person lookup ref from the ID token supplied in the environment.
        person-lur (some-> (environ/env :token)
                           (wna/parse-token)
                           (select-keys [:email])
                           (set/rename-keys {:email :person/email})
                           (find :person/email))]
    (when-not person-lur
      (throw (ex-info "Auth information not set - please set the TOKEN environment variable."
                      {})))
    (let [event-ident (keyword "event" (str "import-" ent-ns))
          prov {:db/id "datomic.tx"
                :provenance/what event-ident
                :provenance/who person-lur
                :provenance/when (wu/now)
                :provenance/how :agent/importer}
          reg-prov (assoc prov :provenance/what :event/new-entity-type)
          ent-named? (-> (wnip/parse-tsv tsv-path) first count) ; count columns to determine if named/un-named
          conf (make-export-conf ent-ns ent-named?)
          name-required? (some #(= (name %) "name") (keys conf))]
      (wne/register-entity-schema conn
                                  id-ident
                                  id-template
                                  reg-prov
                                  true
                                  true
                                  name-required?)
      (doseq [batch (build-data-txes tsv-path conf ent-ns)]
        @(wnip/transact-batch event-ident
                              conn
                              (conj batch (assoc prov :batch/id (d/squuid))))))))

(defn process
  ([conn ent-ns id-template data-tsv-path]
   (process conn ent-ns data-tsv-path 10))
  ([conn ent-ns id-template data-tsv-path n-in-flight]
   (let []
     (batch-transact-data conn ent-ns id-template data-tsv-path))))
