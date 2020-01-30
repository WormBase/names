(ns wormbase.names.importers.gene
  "Import gene data from an ACeDB export.

  Gene data comes in the form of two TSV files:
    1. a set of the 'current data'
    2. a set of the historic actions that have occurred over time.

  The 'current data' set is transated with event type :event/import
  The histoic actions are recorded after-the-fact by transacting provenance against
  the current data set; this results in empty changesets for historic actions as its
  not possible to re-create the history of changes since these have been lost (in ACeDB)."
  (:require
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [datomic.api :as d]
   [wormbase.specs.gene :as wsg]
   [wormbase.names.util :as wnu]
   [wormbase.util :as wu]
   [wormbase.names.importers.processing :as wnip])
  (:gen-class))

(def geneace-text-ref #(last (str/split % #"\s")))

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

(def conform-to-non-empty-value
  [func value]
  (fn conform-non-empty [v]
    (when-not (str/blank v)
      (func v))))

(defn ->biotype [v]
  ((conform-to-non-empty-value (partial wnip/conformed-label ::biotype))) v)

(defn ->species [v]
  (let [[ident value] (wnip/conformed-ref :species/latin-name)]
    (when-not value
      (throw (ex-info "Empty species values are not permitted."
                      {:value v
                       :spec (s/describe :species/latin-name)
                       :ident ident})))))

(defn ->sequence-name [v]
  ((conform-to-non-empty-value (partial wnip/conformed :gene/sequence-name))) v)

(defn ->cgc-name [v]
  ((conform-to-non-empty-value (partial wnip/conformed :gene/cgc-name))) v)

(s/def ::event
  (s/and
   string?
   (s/or :event/import #(str/starts-with? % "Imported")
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
        (wu/discard-empty-valued-entries))))

(defn decode-name-change-event [m target-attr event-text]
  (-> m
      (assoc :provenance/what :event/update-gene)
      (assoc target-attr (geneace-text-ref event-text))))

(defn decode-ref-event [m target-attr event-text event-type]
  (-> m
      (assoc :provenance/what event-type)
      (assoc target-attr [:gene/id (geneace-text-ref event-text)])))

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
      (decode-ref-event :gene/merges what :event/merge-genes)

      (str/starts-with? what "Split_into")
      (decode-ref-event :gene/splits what :event/split-gene)

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
       (wu/sort-events-by :provenance/when)
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
    [gid (wu/sort-events-by :provenance/when fixedup-events)]))

(defn map-history-actions [tsv-path]
  (let [ev-ex-conf (:events export-conf)
        cast-fns {:gene/id cast-gene-id-fn
                  :provenance/who wnip/->who
                  :provenance/when wnip/->when
                  :geneace/event-text identity}]
    (with-open [in-file (io/reader tsv-path)]
      (->> (wnip/parse-tsv in-file)
           (wnip/transform-cast ev-ex-conf cast-fns)
           (group-by :gene/id)
           (map process-gene-events)
           (into {})
           (doall)))))

(defn chuck-on-nils [data]
  (doseq [[k v] (->> data (take-last 2) (apply array-map))]
    (when (nil? v)
      (throw (ex-info (str "value for " k " was nil")
                      {:data data})))))

(defn transact-gene-event
  "Record historical events for the current gene set."
  [conn historical-version event]
  (let [pv (wnu/select-keys-with-ns event "provenance")
        gd (wnu/select-keys-with-ns event "gene")
        lur (find gd :gene/id)
        gid (second lur)
        prov (assoc pv :db/id "datomic.tx" :provenance/how :agent/importer)
        merges-and-splits (mapcat vec (select-keys gd [:gene/merges :gene/splits]))
        tx-data (conj (concat [[:db/add gid :gene/id gid]]
                              [[:db/add gid :importer/historical-gene-version historical-version]]
                              (if (seq merges-and-splits)
                                [(vec (concat [:db/add lur] merges-and-splits))]))
                      prov)
        [data prov] tx-data]
    (chuck-on-nils data)
    (chuck-on-nils prov)
    (wnip/noisy-transact conn tx-data)))

(defn batch-data
  "Build the current entity representation of each gene."
  [data ent-ns filter-fn & {:keys [batch-size]
                            :or {batch-size 500}}]
  (->> data
       (filter filter-fn)
       (partition-all batch-size)))

(defn fixup-non-live-gene
  "Remove names from dead genes that have already been assigned.

  This is required due to the messiness of the export data/ACeDB data source."
  [db gene]
  (cond-> gene
    (d/entity db (find gene :gene/cgc-name))
    (dissoc :gene/cgc-name)

    (d/entity db (find gene :gene/sequence-name))
    (dissoc :gene/sequence-name)))

(defn transact-current-data
  "Import the current set of data and transact into the database.

  Dead genes are transacted one-by-one with special care taken to avoid
  storing duplicate names."
  [conn data ent-ns person-lur]
  (wnip/transact-entities conn
                          data
                          ent-ns
                          person-lur
                          batch-data
                          [:gene/cgc-name :gene/sequence-name]))

(defn process
  ([conn ent-ns id-template data-tsv-path actions-tsv-path]
   (process conn ent-ns id-template data-tsv-path actions-tsv-path 10))
  ([conn ent-ns id-template data-tsv-path actions-tsv-path n-in-flight]
   (let [person-lur (wnip/default-who)
         id-ident (keyword ent-ns "id")
         cast-fns {:gene/id cast-gene-id-fn
                   :gene/species ->species
                   :gene/status (partial wnip/->status ent-ns)
                   :gene/cgc-name ->cgc-name
                   :gene/sequence-name ->sequence-name
                  :gene/biotype ->biotype}
         data (wnip/read-data data-tsv-path (:data export-conf) ent-ns cast-fns)]
     (transact-current-data conn data ent-ns person-lur)
     (doseq [[gene-id gene-events] (map-history-actions actions-tsv-path)]
       (let [event-txes (->> gene-events
                             (keep-indexed #(vector (inc %1) %2))
                             (pmap (partial apply transact-gene-event conn)))]
         ;; keep `n-in-fllght` transactions running at a time for performance.
         (doseq [event-txes-p (partition-all n-in-flight event-txes)]
           (doseq [event-tx event-txes-p]
             @event-tx)))))))
