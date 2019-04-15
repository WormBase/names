(ns wormbase.names.importers.gene
  "Import gene data from an ACeDB export.

  Gene data comes in the form of two TSV files:
    1. a set of the 'current data'
    2. a set of the historic actions that have occurred over time.

  The 'current data' set is transated with event type :event/import-gene.
  The histoic actions are recorded after-the-fact by transacting provenance against
  the current data set; this results in empty changesets for historic actions as its
  not possible to re-create the history of changes since these have been lost (in ACeDB)."
  (:require
   [clojure.java.io :as io]
   [clojure.set :refer [rename-keys]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.walk :as w]
   [cheshire.core :as json]
   [clojure.set :as set]
   [datomic.api :as d]
   [environ.core :refer [env]]
   [mount.core :as mount]
   [ring.util.http-response :as http-response]
   [wormbase.db :as wdb]
   [wormbase.db.schema :as wdbs]
   [wormbase.specs.gene :as wsg]
   [wormbase.specs.person :as wsp]
   [wormbase.specs.species :as wss]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.names.importers.processing :as wnip])
  (:gen-class))

(def deferred (atom {}))

(def geneace-text-ref #(last (str/split % #"\s")))

(defn transact-batch [conn tx-data event-type & {:keys [transact-fn]}]
  (try
    (wnip/transact-batch event-type conn tx-data :tranasct-fn transact-fn)
    (catch Exception exc
      (println "EXCEPTION!!!!")
      (println "EX-DATA follows:")
      (prn (ex-data exc))
      (println)
      (throw exc))))

(defn handle-transact-exc [exc data]
  (throw (ex-info "Failed to transact data!"
                  {:data data
                   :orig-exc exc})))

(defn noisy-transact [conn data]
  (try
    (d/transact-async conn data)
    (catch java.util.concurrent.ExecutionException exc
      (handle-transact-exc exc data))
    (catch java.lang.IllegalArgumentException exc
      (handle-transact-exc exc data))
    (catch Exception exc
      (handle-transact-exc exc data))))

(defn defer-tx [d event]
  (update d :tx-data conj event))

(defn defer-event [event data-attr event-type event-text]
  (let [from-lur (find event :gene/id)
        into-lur [:gene/id (geneace-text-ref event-text)]
        prov (-> event
                 (wnu/select-keys-with-ns "provenance")
                 (assoc :db/id "datomic.tx"
                        :provenance/what event-type
                        :provenance/how :agent/importer))]
    (swap! deferred
           defer-tx
           [[:db/add from-lur data-attr into-lur] prov]))
    nil)

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
               :biotype/pseudogene #(= % "Pseudogene"))))

(defn ->biotype [v]
  (when-not (str/blank? v)
    (wnip/conformed-label ::biotype v)))

(s/def ::event
  (s/and
   string?
   (s/or :event/import-gene #(str/starts-with? % "Imported")
         :event/resurrect-gene (partial = "Resurrected")
         :event/new-unnamed-gene (partial = "Allocate")
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

(defn decode-biotype-event [m event-text]
  (let [[bt-from bt-to] (-> event-text
                            (str/split #"\s" 3)
                            rest)]
    (-> m
        (assoc :provenance/what :event/update-gene)
        (wnip/discard-empty-valued-entries))))

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

      (str/starts-with? what "Merged_into")
      (defer-event :gene/merges :event/merge-genes what)

      (str/starts-with? what "Split_into")
      (defer-event :gene/splits :event/split-gene what)

      (= what "Killed")
      (assoc :provenance/what :event/kill-gene)

      (= what "Suppressed")
      (assoc :provenance/what :event/suppress-gene)

      (= what "Resurrected")
      (assoc :provenance/what :event/resurrect-gene)

      (= what "Created")
      (assoc :provenance/what :event/new-gene))))

(def cast-gene-id-fn (partial wnip/conformed :gene/id))

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
       (wnp/sort-events-by :provenance/when)
       last))

(defn process-gene-events [[gid events]]
  (let [decoded-events (remove nil? (map decode-geneace-event events))
        first-created (choose-first-created-event
                       decoded-events)
        no-created (remove
                    #(= (:geneace/event-text %) "Created")
                    decoded-events)
        fixedup-events (if first-created
                         (conj no-created first-created)
                         no-created)]
    [gid (wnp/sort-events-by :provenance/when fixedup-events)]))

(defn map-history-actions [tsv-path]
  (let [ev-ex-conf (:events export-conf)
        cast-fns {:gene/id cast-gene-id-fn
                  :provenance/who (partial wnip/conformed-ref :person/id)
                  :provenance/when wnip/->when
                  :geneace/event-text identity}]
    (with-open [in-file (io/reader tsv-path)]
      (->> (wnip/parse-transform-cast in-file ev-ex-conf cast-fns)
           (group-by :gene/id)
           (map process-gene-events)
           (into {})
           (doall)))))

(defn chuck-on-nils [data]
  (doseq [[k v] data]
    (when (nil? v)
      (throw (ex-info (str "value for " k " was nil")
                      {:data data})))))

(defn transact-gene-event
  "Record historical events for the current gene set."
  [conn historical-version event]
  (let [pv (wnu/select-keys-with-ns event "provenance")
        tx-data [{:gene/id (:gene/id event)
                  :importer/historical-gene-version historical-version}
                 (assoc pv
                        :db/id "datomic.tx"
                        :provenance/how :agent/importer)]
        [data prov] tx-data]
    (chuck-on-nils data)
    (chuck-on-nils prov)
    (noisy-transact conn tx-data)))

(defn build-data-txes
  "Build the current entity representation of each gene."
  [tsv-path conf filter-fn
   & {:keys [batch-size]
      :or {batch-size 500}}]
  (let [cast-fns {:gene/id cast-gene-id-fn
                  :gene/species (partial wnip/conformed-ref
                                         :species/latin-name)
                  :gene/status (partial wnip/conformed-label ::status)
                  :gene/cgc-name (partial wnip/conformed :gene/cgc-name)
                  :gene/sequence-name (partial wnip/conformed
                                               :gene/sequence-name)
                  :gene/biotype ->biotype}]
    (with-open [in-file (io/reader tsv-path)]
      (->> (wnip/parse-transform-cast in-file conf cast-fns)
           (map wnip/discard-empty-valued-entries)
           (filter filter-fn)
           (partition-all batch-size)
           doall))))

(defn fixup-non-live-gene [db gene]
  (cond-> gene
    (d/entity db (find gene :gene/cgc-name))
    (dissoc :gene/cgc-name)

    (d/entity db (find gene :gene/sequence-name))
    (dissoc :gene/sequence-name)))

(defn process-deferred [conn]
  (doseq [data (:tx-data @deferred)
          tx-data (partition-all 500 data)]
    @(noisy-transact conn tx-data)))

(defn transact-current-data [conn tsv-path]
  (let [cd-ex-conf (:data export-conf)]
    ;; process all genes that are not dead, using the default batch size of 500.
    (doseq [batch (build-data-txes tsv-path
                                   cd-ex-conf
                                   #(not= (:gene/status %) :gene.status/dead))]
      @(transact-batch conn batch :event/import-gene :tranasct-fn noisy-transact))
    ;; post-porcess all dead genes to work around "duplicate names on dead genes" issue:
    ;; - only process the dead genees now, 1 at a time.
    ;; - use batch-size of 1, since there are dead genes with
    ;;   that share names (e.g sequence names)
    (doseq [batch (build-data-txes tsv-path
                                   cd-ex-conf
                                   #(= (:gene/status %) :gene.status/dead)
                                   :batch-size 1)]
      @(transact-batch conn
                       :event/import-gene
                       (map (partial fixup-non-live-gene (d/db conn)) batch)
                       :tranasct-fn noisy-transact))))

(defn process
  [conn data-tsv-path actions-tsv-path & {:keys [n-in-flight]
                                          :or {n-in-flight 10}}]
  (transact-current-data conn data-tsv-path)
  (doseq [[gene-id gene-events] (map-history-actions actions-tsv-path)]
    (let [event-txes (->> gene-events
                          (keep-indexed #(vector (inc %1) %2))
                          (pmap (partial apply transact-gene-event conn)))]
      ;; keep `n-in-fllght` transactions  running at a time for performance.
      (doseq [event-txes-p (partition-all n-in-flight event-txes)]
        (doseq [event-tx event-txes-p]
          @event-tx))))
  (process-deferred conn))

(defn process-merges-and-splits
  [conn actions-tsv-path & {:keys [n-in-flight]
                            :or {n-in-flight 10}}]
  (dorun (map-history-actions actions-tsv-path))
  (process-deferred conn))
