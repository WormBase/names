(ns wormbase.names.importer
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [datomic.api :as d]
   [environ.core :refer [env]]
   [mount.core :as mount]
   [wormbase.db :as wdb]
   [wormbase.db.schema :as wdbs]
   [wormbase.names.importers.processing :as wnip]
   [wormbase.names.importers.entity :as ent-importer]
   [wormbase.names.importers.gene :as gene-importer]))

(def cli-options [])

(defn usage [_]
  (str/join
   \newline
   ["Imports data from TSV export files from GeneACe into the name-service's datomic database."]))

(defn exit [status msg]
  (println msg)
  (System/exit status))

;; A mapping of entity-namespace to importer.
;; Any name passed on the CLI that isn't in this map is considered generic.
;; Add a function here for any furture concrete importers (those with custom schema and rules)
(def importers {"gene" gene-importer/process})

(defn -main
  "Command line entry point.

   Runs the application without the change queue mointor and executes
   the import process."
  [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)
        db-uri (env :wb-db-uri)
        [importer-ns-name id-template & tsv-paths] arguments]
    (d/create-database db-uri)
    (mount/start)
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (str/join "\n" errors))
      (nil? importer-ns-name)
      (exit 1 (str "Please specifiy the importer to run, .e.g: \""
                   (str/join "," (-> importers keys sort))
                   "\""))

      (nil? id-template)
      (exit 1 "Please supply the identifier template for the entity type, e.g: \"WBGene%08d\"")

      (empty? tsv-paths)
      (exit 1 (str (if (get importers importer-ns-name)
                     "Pass the 2 .tsv files as first 2 parameters for "
                     "Please pass the single TSV file for ")
                   importer-ns-name
                   " import."))

      (not (every? #(.exists (io/file %)) tsv-paths))
      (let [non-existant (filter #(not (.exists (io/file %))) tsv-paths)]
        (exit 2 (str "A .tsv file did not exist, check paths supplied: "
                     (str/join ", " non-existant))))

      (nil? (env :wb-db-uri))
      (exit 1 "set the WB_DB_URI environement variable w/datomic URI.")

      tsv-paths
      (if-let [importer (get importers importer-ns-name ent-importer/process)]
        (let [process-import (partial importer wdb/conn importer-ns-name id-template)]
          (wnip/check-environ!)
          (wdbs/ensure-schema wdb/conn)
          (print "Importing...")
          (apply process-import tsv-paths)
          (println "[ok]")
          (mount/stop)
          (exit 0 "Finished"))
        (exit 1 (str "Invalid importer specified:" importer-ns-name))))))
