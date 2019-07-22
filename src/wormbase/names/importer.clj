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
   [wormbase.names.importers.gene :as gene-importer]
   [wormbase.names.importers.variation :as var-importer]))

(def cli-options [])

(defn usage [_]
  (str/join
   \newline
   ["Imports data from TSV export files from GeneACe into the name-service's datomic database."]))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(def importers {"gene" gene-importer/process
                "variation" var-importer/process})

(defn -main
  "Command line entry point.

   Runs the application without the change queue mointor and executes
   the import process."
  [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)
        db-uri (env :wb-db-uri)
        importer-ns-name (first arguments)
        tsv-paths (take 2 (rest arguments))]
    (mount/start)
    (wdbs/apply-updates!)
    (wdbs/import-people wdb/conn)
    (cond
      (:help options) (exit 0 (usage summary))

      (nil? (get importers importer-ns-name))
      (exit 1 (str "Please specifiy the importer to run, one of: "
                   (str/join "," (-> importers keys sort))
                   "args: " (pr-str arguments)))

      (empty? tsv-paths)
      (exit 1 "Pass the 2 .tsv files as first 2 parameters")

      (not (every? #(.exists (io/file %)) tsv-paths))
      (let [non-existant (filter #(not (.exists (io/file %))) tsv-paths)]
        (exit 2 (str "A .tsv file did not exist, check paths supplied: "
                     (str/join ", " non-existant))))

      (nil? (env :wb-db-uri))
      (exit 1 "set the WB_DB_URI environement variable w/datomic URI.")

      tsv-paths
      (if-let [importer (get importers importer-ns-name)]
        (let [process-import (partial importer wdb/conn)]
          (print "Importing...")
          (apply process-import tsv-paths)
          (println "[ok]")
          (mount/stop)
          (exit 0 "Finished"))
        (exit 1 (str "Invalid importer specified:" importer-ns-name))))))


