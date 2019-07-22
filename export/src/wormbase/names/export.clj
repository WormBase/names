(ns wormbase.names.export
  (:require
   [clojure.java.io :as io]
   [clojure.data.csv :as cd-csv]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [datomic.api :as d]
   [environ.core :refer [env]]
   [semantic-csv.core :as sc])
  (:import
   (java.util.concurrent ExecutionException)))

(defn abbrev-ident [entity]
  (when-let [ident (:db/ident entity)]
    (name ident)))

(defn export-data [out-path db ident pull-expr cast-with]
  (with-open [out-file (io/writer out-path)]
    (->> (d/q '[:find [(pull ?e pattern) ...]
                :in $ ?ident pattern
                :where
                [?e ?ident ?id]]
              db
              ident
              pull-expr)
         (sc/cast-with cast-with)
         (sc/vectorize)
         (cd-csv/write-csv out-file))))

(defn export-genes [out-path db]
  (export-data out-path
               db
               :gene/id
               '[:gene/id
                 :gene/cgc-name
                 :gene/sequence-name
                 {:gene/status [[:db/ident]]
                  :gene/biotype [[:db/ident]]}]
               {:gene/biotype abbrev-ident
                :gene/status abbrev-ident}))

(defn anonymous-variations [out-path db]
  (with-open [out-file (io/writer out-path)]
    (->> (d/q '[:find [(pull ?e pattern) ...]
                :in $ ?ident pattern
                :where
                [(missing? $ ?e :variation/name)]
                [?e ?ident ?id]]
              db
              :variation/id
              '[:variation/id :variation/name
                {:variation/status [[:db/ident]]}])
         (sc/cast-with {:variation/status abbrev-ident})
         (sc/vectorize)
         (cd-csv/write-csv out-file))))

(defn export-variations [out-path db]
  (export-data out-path
               db
               :variation/id
               '[:variation/id :variation/name
                 {:variation/status [[:db/ident]]}]
               {:variation/status abbrev-ident}))

(def dispatch {:genes export-genes
               :variations export-variations
               :vnpn anonymous-variations})

(def cli-options [])

(defn usage [_]
  (str/join \newline
            ["Exports records for a given entity type from the names service."]))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn connect [uri]
  (try
    (d/connect uri)
    (catch ExecutionException ex
      (println "[fail]")
      (exit 1 (str "Ensure you have set your creedntials via environment variables:"
                   "AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY")))))

(defn run [db-uri export-what out-path]
  (print "CONNECTING to datomic database at:" db-uri " ... ")
  (let [conn (connect db-uri)
        db (d/db conn)
        export-fn (-> export-what keyword dispatch)]
    (println "[ok]")
    (print "Outputing" export-what "to" out-path " ... ")
    (flush)
    (export-fn out-path db)
    (println "[ok]")
    (d/release conn))
  (System/exit 0))

(defn -main
  "Command line entry point."
  [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)
        db-uri (env :wb-db-uri)
        [export-what out-path] arguments]
    (cond
      (:help options) (exit 0 (usage summary))
      (nil? db-uri) (exit 1 (str "Please set the WB_DB_URI environment variable.\n"
                                 "e.g: export WB_DB_URI=\"datomic:ddb://us-east-1/WSNames/20190503_12\""))
      (nil? export-what) (exit 1 (str "Available options are:"
                                      (str/join ", " (map name (keys dispatch)))))
      (nil? out-path) (exit 1 "Specify the output file path. e.g: /tmp/foo.csv"))
    (run db-uri export-what out-path)))
