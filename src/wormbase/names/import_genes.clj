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
   [wormbase.specs.gene :as wsg]
   [wormbase.names.event-broadcast :as wneb]
   [wormbase.specs.person :as wsp]
   [wormbase.specs.species :as wss]
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

;; conforming this should produce the right ident
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

(defn sort-events-by-when
  "Sort a sequence of mappings representing events in temporal order."
  [events & {:keys [most-recent-first]
             :or {most-recent-first false}}]
  (let [cmp (if most-recent-first
              #(compare %2 %1)
              #(compare %1 %2))]
    (sort-by :provenance/when cmp events)))

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
       sort-events-by-when
       last))

(defn parse-transform-cast [in-file conf cast-fns]
  (->> (parse-tsv in-file)
       (sc/mappify (select-keys conf [:header]))
       (sc/cast-with cast-fns {:exception-handler handle-cast-exc})))

(defn map-history-actions [tsv-path]
  (let [ev-ex-conf (:events export-conf)
        cast-fns {:gene/id cast-gene-id-fn
                  :provenance/who (partial conformed-ref :person/id)
                  :provenance/when ->when
                  :geneace/event-text identity}]
        (with-open [in-file (io/reader tsv-path)]
          (doall
           (->> (parse-transform-cast in-file ev-ex-conf cast-fns)
                (sort-by :provenance/when)
                (group-by :gene/id)
                (map
                 (fn process-gene-events [[gid events]]
                   (let [decoded-events (map decode-geneace-event events)
                         first-created (choose-first-created-event
                                        decoded-events)
                         no-created (remove
                                     #(= (:geneace/event-text %) "Created")
                                     decoded-events)
                         fixedup-events (if first-created
                                          (conj no-created first-created)
                                          no-created)]
                     [gid (sort-by :provenance/when fixedup-events)])))
                (into {}))))))

(defn current-data [tsv-path]
  (let [cd-ex-conf (:data export-conf)
        cast-fns {:gene/id cast-gene-id-fn
                  :gene/species (partial conformed-ref
                                         :species/latin-name)
                  :gene/status (partial conformed-label ::status)
                  :gene/cgc-name (partial conformed :gene/cgc-name)
                  :gene/sequence-name (partial conformed
                                               :gene/sequence-name)
                  :gene/biotype ->biotype}]
    (with-open [in-file (io/reader tsv-path)]
      (doall
       (->> (parse-transform-cast in-file cd-ex-conf cast-fns)
            (map (fn [data]
                   [(:gene/id data) data]))
            (into {}))))))

(defn filter-by-event-type [et events]
  (filter #(= (:provenance/what %) et) events))

(defn transact [conn tx-data]
  (d/transact-async conn tx-data))

(defn transact-gene [conn gene]
  (d/transact-async
   conn
   [(if (= (:gene/status gene) :gene.status/dead)
      (dissoc gene :gene/sequence-name :gene/cgc-name)
      gene)
    {:db/id "datomic.tx"
     :provenance/what :event/import-gene
     :provenance/how :agent/importer}]))

(defn transact-gene-event [conn historical-version event]
  (let [pv (wnu/select-keys-with-ns event "provenance")
        tx-data [{:gene/id (:gene/id event)
                  :importer/historical-gene-version historical-version}
                 (assoc pv :db/id "datomic.tx")]]
    (d/transact-async conn tx-data)))

(defn process [conn current-data-tsv-path history-actions-tsv-path]
  (let [all-events (map-history-actions history-actions-tsv-path)
        curr-data (->> current-data-tsv-path
                       current-data
                       vals
                       (map discard-empty-valued-entries))]
    (doseq [data-tx (pmap (partial transact-gene conn) curr-data)]
      (deref data-tx))
    (doseq [gene curr-data]
      (let [gene-events (->> (get all-events (:gene/id gene))
                             (sort-events-by-when)
                             (keep-indexed (fn [i e]
                                             [(inc i) e])))
            event-txes (pmap (partial apply transact-gene-event conn) gene-events)]
        (doseq [event-tx event-txes]
          (deref event-tx))))))

(def cli-options [])

(defn usage [_]
  (str/join
   \newline
   ["Imports genes from a .tsv export from Geneace into the datomic database."]))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main
  "Entry point designed to be invoked via the command line.
   Runs the application without the change queue mointor and executes
   the import process."
  [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)
        tsv-path (first arguments)]
    (cond
      (:help options) (exit 0 (usage summary))

      (not tsv-path)
      (exit 1 "Pass .tsv file as first parameter")

      (not (.exists (io/file tsv-path)))
      (exit 2 ".tsv file does not exist")

      :othwrwise
      (println "RUN?")
;;      (run (d/connect (env :wb-db-uri)) tsv-path)
      )))
