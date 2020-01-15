(ns wormbase.names.export
  (:require
   [clojure.java.io :as io]
   [clojure.data.csv :as cd-csv]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [datomic.api :as d]
   [environ.core :refer [env]]
   [semantic-csv.core :as sc])
  (:import
   (java.util.concurrent ExecutionException)))


(def space-join (partial str/join " "))

(defn collapse-space
  "Remove occruances of multiple spaces in `s` with a single space."
  [s]
  (str/replace s #"\s{2,}" " "))


(defn abbrev-ident
  "For a given `entity`, abbreviate a datomic `ident`.
  An `ident` is a namespaced keyword.
  Return the `name` component of the keyword,
  iif the `ident` is present in the database."
  [entity]
  (when-let [ident (:db/ident entity)]
    (name ident)))

(defn export-data
  "Export data from database to a CSV file."
  [out-path db ident pull-expr cast-with]
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

(defn export-genes
  "Export all genes to a CSV file."
  [out-path db ent-ns]
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

(defn export-entities
  "Export all records for a given entity type to a CSV file."
  [out-path db ent-ns]
  (let [id-ident (keyword ent-ns "id")
        status-ident (keyword ent-ns "status")
        name-ident (keyword ent-ns "name")
        named? (:wormbase.names/name-required? (d/entity db id-ident))
        pull-expr (conj [id-ident {status-ident [[:db/ident]]}]
                        (when named?
                          name-ident))]
    (if named?
      (export-data out-path db id-ident pull-expr {status-ident abbrev-ident})
      (with-open [out-file (io/writer out-path)]
        (let [max-id (d/q '[:find (max ?id) . :in $ ?ident :where [?e ?ident ?id]]
                          db
                          id-ident)]
          (cd-csv/write-csv out-file [[max-id]]))))))

(defn list-available-entitiy-types
  "List the entity types available for export."
  [db]
  (->> (d/q '[:find ?entity-type ?generic ?enabled
              :keys entity-type generic? enabled?
              :in $
              :where
              [?et :wormbase.names/entity-type-enabled? ?enabled]
              [?et :wormbase.names/entity-type-generic? ?generic]
              [?et :db/ident ?entity-type]]
            db)
       (map #(update % :entity-type namespace))
       (map #(update % :enabled? (fnil identity true)))
       (filter :enabled?)
       (map :entity-type)
       (sort)
       (str/join "\n")))

(def cli-options [[nil
                   "--list"
                   "Display the enabled entity types in the system."]])

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn connect [uri]
  (try
    (print "Connecting to datomic database at:" uri " ... ")
    (let [conn (d/connect uri)]
      (println "[ok]")
      conn)
    (catch ExecutionException ex
      (println "[fail]")
      (exit 1 (str "Ensure you have set your creedntials via environment variables:"
                   "AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY")))))

(defn run [db ent-ns out-path]
  (let [export-fn (if (= ent-ns "gene")
                    export-genes
                    export-entities)]
    (print "Outputing" ent-ns "to" out-path " ... ")
    (flush)
    (export-fn out-path db ent-ns)
    (println "[ok]")))



(def cli-actions [#'export-genes
                  #'export-entities])

(def cli-action-metas (map meta cli-actions))

(def cli-action-map (zipmap
                     (map #(str (:name %)) cli-action-metas)
                     cli-actions))

(def cli-doc-map (into
                  {}
                  (for [m cli-action-metas]
                    {(#(str (:name %)) m) (:doc m)})))

(defn usage
  "Display command usage to the user."
  [options-summary]
  (let [action-names (keys cli-doc-map)
        action-docs (vals cli-doc-map)
        doc-width-left (+ 10 (apply max (map count action-docs)))
        action-width-right (+ 10 (apply max (map count action-names)))
        line-template (str "%-"
                           action-width-right
                           "s%-"
                           doc-width-left
                           "s")]
    (str/join
     \newline
     (concat
      [(str "This is tool for export data from the WormBase Names Service "
            "into CSV files.")
       ""
       "Usage: -m wormbase.names.export [options] action"
       ""
       "Options:"
       options-summary
       ""
       (str "Actions: (required options for each action "
            "are provided in square brackets)")]
      (for [action-name (sort action-names)
            :let [doc-string (cli-doc-map action-name)]]
        (let [usage-doc (some-> doc-string
                                str/split-lines
                                space-join
                                collapse-space)]
          (format line-template action-name usage-doc)))))))


(defn -main
  "Command line entry point."
  [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)
        db-uri (env :wb-db-uri)
        conn (connect db-uri)
        db (d/db conn)
        [ent-ns out-path] arguments]
    (cond
      (:help options) (exit 0 (usage summary))
      (:list options) (exit 0 (list-available-entitiy-types db))
      (nil? db-uri) (exit 1 (str "Please set the WB_DB_URI environment variable.\n"
                                 "e.g: export WB_DB_URI=\"datomic:ddb://us-east-1/WSNames/20190503_12\""))
      (nil? ent-ns) (exit 1 (str "Please specify an entity type to export. Use --list to available types."))
      (nil? out-path) (exit 1 "Specify the output file path. e.g: /tmp/foo.csv"))
    (run db ent-ns out-path)
    (d/release conn)
    (System/exit 0)))
