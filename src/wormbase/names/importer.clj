(ns wormbase.names.importer
  (:require
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
;   [datomic.api :as d]
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
        tsv-paths (take 2 (rest arguments))
        db-uri (env :wb-db-uri)
        importer-ns-name (first arguments)]
    (cond
      (:help options) (exit 0 (usage summary))

      (empty? tsv-paths)
      (exit 1 "Pass the 2 .tsv files as first 2 parameters")

      (not (every? #(.exists (io/file %)) tsv-paths))
      (let [non-existant (filter #(not (.exists (io/file %))) tsv-paths)]
        (exit 2 (str "A .tsv file did not exist, check paths supplied"
                     (str/join ", " non-existant))))

      (nil? (env :wb-db-uri))
      (exit 1 "set the WB_DB_URI environement variable w/datomic URI.")

      (importer-ns-name importers)
      (exit 1 "Please specifiy the importer to run, one of:"
            (str/join "," (-> importers keys sort)))

      tsv-paths
      (do (if (true? (d/create-database db-uri))
            (let [conn (d/connect db-uri)
                  importer (get importers importer-ns-name)]
              (print "Installing schema... ")
              (wdbs/install conn 1)
              (println "done")
              (d/release conn)
              (println "DB installed at:" db-uri))
            (println "Assuming schema has been installed."))
          (let [conn (d/connect db-uri)
                process-import (partial importer conn)]
            (print "Importing...")
            (apply process-import tsv-paths)
            (println "[ok]"))))))


