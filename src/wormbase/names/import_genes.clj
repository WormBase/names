(ns wormbase.names.import-genes
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.set :refer [rename-keys]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [clojure.walk :as w]
   [cheshire.core :as json]
   [clojure.set :as set]
   [datomic.api :as d]
   [environ.core :refer [env]]
   [java-time :as jt]
   [mount.core :as mount]
   [ring.util.http-response :as http-response]
   [semantic-csv.core :as sc]
   [wormbase.db :as wdb]
   [wormbase.db.schema :as wdbs]
   [wormbase.specs.gene :as wsg]
   [wormbase.names.event-broadcast :as wneb]
   [wormbase.specs.person :as wsp]
   [wormbase.specs.species :as wss]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu])
  (:import
   (clojure.lang ExceptionInfo)
   (java.time.format DateTimeFormatter))
  (:gen-class))

(defn- throw-parse-exc! [spec value]
  (throw (ex-info "Could not parse"
                  {:spec spec
                   :value value
                   :problems (s/explain-data spec value)})))

(defn discard-empty-valued-entries [data]
  (reduce-kv (fn [m k v]
               (if (nil? v)
                 (dissoc m k)
                 m))
             data
             data))

(defn conformed [spec value & {:keys [transform]
                               :or {transform identity}}]
  (cond
    (str/blank? value) nil
    (s/valid? spec value) (transform (s/conform spec value))
    :else (throw-parse-exc! spec value)))

(defn conformed-ref [spec value]
  [spec (conformed spec value)])

(defn conformed-label [or-spec value]
  (conformed or-spec value :transform first))

(defn ->when
  "Convert the provenance timestamp into a time-zone aware datetime."
  [value & {:keys [tz] :or {tz "UTC"}}]
  (-> (jt/local-date-time value)
      (jt/zoned-date-time tz)
      (jt/with-zone-same-instant tz)
      (jt/instant)
      (jt/to-java-date)))

(defn parse-tsv [stream]
  (csv/read-csv stream :separator \tab))

;; A string describing the Gene "HistoryAction".
;; This contains data about the change in some cases (e.g split, merge, change name)
(s/def :geneace/event-text (s/and string? not-empty))

(s/def ::status (s/and string?
                       (s/or :gene.status/live #(= % "Live")
                             :gene.status/dead #(= % "Dead")
                             :gene.status/suppressed #(= % "Suppressed"))))

(s/def ::biotype
  (s/and string?
         (s/or :biotype/transposable-element-gene #(or
                                                    (= % "transposable_element_gene")
                                                    (= % "Transposon In Origin"))
               :biotype/transcript #(= % "Transcript")
               :biotype/cds #(= % "CDS")
               :biotype/psuedogene #(= % "Pseudogene"))))

(defn ->biotype [v]
  (if-not (str/blank? v)
    (conformed-label ::biotype v)))

(s/def ::event
  (s/and
   string?
   (s/or :event/import-gene #(str/starts-with? % "Imported")
         :event/resurrect-gene (partial = "Resurrected")
         :event/new-unnamed-ge>ne (partial = "Allocate")
         :event/new-gene (partial = "Created")
         :event/kill-gene (partial = "Killed")
         :event/update-gene #(or
                              (str/starts-with? % "Changed_class")
                              (str/starts-with? % "CGC_name")
                              (str/starts-with? % "Sequence_name")
                              (= % "Suppressed")
                              (= % "Transposon_in_origin"))
         :event/merge-genes #(str/includes? (str/lower-case %) "merge")
         :event/split-gene #(str/includes? (str/lower-case %) "split"))))

(def geneace-text-ref #(last (str/split % #"\s")))

(defn decode-biotype-event [m event-text]
  (let [[bt-from bt-to] (-> event-text
                            (str/split #"\s" 3)
                            rest)]
    (-> m
        (assoc :provenance/what :event/update-gene)
        (assoc :import-event/biotype-from bt-from)
        (assoc :import-event/biotype-to bt-to)
        (discard-empty-valued-entries))))

(defn decode-merge-event [m event-text]
  (let [merged-into (:gene/id m)
        merged-from (geneace-text-ref event-text)]
    (-> m
        (assoc :provenance/what :event/merge-genes)
        (assoc :provenance/merged-into [:gene/id merged-into])
        (assoc :provenance/merged-from [:gene/id merged-from]))))

(defn decode-split-event [m event-text]
  (let [split-from (:gene/id m)
        split-into (geneace-text-ref event-text)]
    (-> m
        (assoc :provenance/what :event/split-gene)
        (assoc :provenance/split-into [:gene/id split-into])
        (assoc :provenance/split-from [:gene/id split-from]))))

(defn decode-name-change-event [m target-attr event-text]
  (-> m
      (assoc :provenance/what :event/update-gene)
      (assoc target-attr (geneace-text-ref event-text))))

(defn decode-geneace-event [event]
  (let [what (:geneace/event-text event)]
    (cond-> event
      (str/starts-with? what "CGC_name")
      (decode-name-change-event :gene/cgc-name what)

      (str/starts-with? what "Sequence_name")
      (decode-name-change-event :gene/sequence-name what)

      (str/starts-with? what "Changed_class")
      (decode-biotype-event what)

      (str/starts-with? what "Acquires_merge")
      (decode-merge-event what)

      (str/starts-with? what "Split_into")
      (decode-split-event what)

      (= what "Killed")
      (assoc :provenance/what :event/kill-gene)

      (= what "Suppressed")
      (assoc :provenance/what :event/suppress-gene)

      (= what "Resurrected")
      (assoc :provenance/what :event/resurrect-gene)

      (= what "Created")
      (assoc :provenance/what :event/new-gene))))

(defn- handle-cast-exc [& xs]
  (throw (ex-info "Error!" {:x xs})))

(def cast-gene-id-fn (partial conformed :gene/id))

(def ^{:doc "A mapping describing the format of the export files."}
  export-conf {:events {:header [:gene/id
                                 :provenance/who
                                 :provenance/when
                                 :geneace/event-text]}
               :data {:header [:gene/id
                               :gene/species
                               :gene/status
                               :gene/cgc-name
                               :gene/sequence-name
                               :gene/biotype]}})

(defn choose-first-created-event
  "The export from GeneAce (and the ACeDB db itself) has lot of odd
  things in the hisory.

  Notes:

  - A gene split event (that creates a new gene) does not produce a
  created event for a given gene id.

  - An \"Import\" event gets translated to a \"Created\" event by the
  exporter, since not all genes will have a created event.

  - Some cases of genes being
  \"imported\" more than once (data fudging)."
  [events]
  (->> (filter #(= (:provenance/what %) :event/new-gene) events)
       wnp/sort-events-by-when
       last))

(defn parse-transform-cast [in-file conf cast-fns]
  (->> (parse-tsv in-file)
       (sc/mappify (select-keys conf [:header]))
       (sc/cast-with cast-fns {:exception-handler handle-cast-exc})))

(defn process-gene-events [[gid events]]
  (let [decoded-events (map decode-geneace-event events)
        first-created (choose-first-created-event
                       decoded-events)
        no-created (remove
                    #(= (:geneace/event-text %) "Created")
                    decoded-events)
        fixedup-events (if first-created
                         (conj no-created first-created)
                         no-created)]
    [gid (wnp/sort-events-by-when fixedup-events)]))

(defn map-history-actions [tsv-path]
  (let [ev-ex-conf (:events export-conf)
        cast-fns {:gene/id cast-gene-id-fn
                  :provenance/who (partial conformed-ref :person/id)
                  :provenance/when ->when
                  :geneace/event-text identity}]
    (with-open [in-file (io/reader tsv-path)]
      (->> (parse-transform-cast in-file ev-ex-conf cast-fns)
           (group-by :gene/id)
           (map process-gene-events)
           (into {})
           (doall)))))

(defn transact-gene-event
  "Record historical events for the current gene set."
  [conn historical-version event]
  (let [pv (wnu/select-keys-with-ns event "provenance")
        tx-data [{:gene/id (:gene/id event)
                  :importer/historical-gene-version historical-version}
                 (assoc pv :db/id "datomic.tx")]]
    (d/transact-async conn tx-data)))

(defn build-data-txes
  "Build the current entity representation of each gene."
  [tsv-path conf & {:keys [batch-size]
                    :or {batch-size 1000}}]
  (let [cast-fns {:gene/id cast-gene-id-fn
                  :gene/species (partial conformed-ref
                                         :species/latin-name)
                  :gene/status (partial conformed-label ::status)
                  :gene/cgc-name (partial conformed :gene/cgc-name)
                  :gene/sequence-name (partial conformed
                                               :gene/sequence-name)
                  :gene/biotype ->biotype}]
    (with-open [in-file (io/reader tsv-path)]
      (->> (parse-transform-cast in-file conf cast-fns)
           (map discard-empty-valued-entries)
           (partition-all batch-size)
           doall))))

(defn transact-batch [conn tx-batch]
  (let [tx-data (conj tx-batch {:db/id "datomic.tx"
                                :provenance/what :event/import-gene
                                :provenance/how :agent/importer})]
    (d/transact-async conn tx-data)))

(defn batch-transact-data [conn tsv-path]
  (let [cd-ex-conf (:data export-conf)]
    (doseq [txf (pmap (partial transact-batch conn)
                      (build-data-txes tsv-path cd-ex-conf))]
      @txf)))

(defn process
  [conn data-tsv-path actions-tsv-path & {:keys [n-in-flight]
                                          :or {n-in-flight 10}}]
  (batch-transact-data conn data-tsv-path)
  (doseq [[gene-id gene-events] (map-history-actions actions-tsv-path)]
    (let [event-txes (->> gene-events
                          (keep-indexed #(vector (inc %1) %2))
                          (pmap (partial apply transact-gene-event conn)))]
      ;; keep `n-in-fllght` transactions  running at a time for performance.
      (doseq [event-txes-p (partition-all n-in-flight event-txes)]
        (doseq [event-tx event-txes-p]
          @event-tx)))))

(def cli-options [])

(defn usage [_]
  (str/join
   \newline
   ["Imports genes from a .tsv export from Geneace into the datomic database."]))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main
  "Command line entry point.

   Runs the application without the change queue mointor and executes
   the import process."
  [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)
        tsv-paths (take 2 (rest arguments))
        db-uri (env :wb-db-uri)]
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

      tsv-paths
      (do (when-not (d/create-database db-uri)
            (let [conn (d/connect db-uri) ]
              (wdbs/install conn 1)
              (d/release conn)))
          (let [conn (d/connect db-uri)
                proc (partial process conn)]
            (apply proc tsv-paths))))))


