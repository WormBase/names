(ns wormbase.specs.gene
  (:require
   [clojure.string :as str]
   [clojure.spec.alpha :as s]
   [phrase.alpha :as ph]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]
   [wormbase.specs.biotype :as wsb]
   [wormbase.specs.provenance :as wsp]
   [wormbase.specs.species :as wss]))


(def gene-id-regexp #"WBGene\d{8}")

(s/def ::name (s/and string?))

(s/def :gene/id (stc/spec {:spec ::name
                           :swagger/example "WBGene00000421"
                           :description "The primary identifier of a Gene."}))

(s/def :gene/cgc-name (stc/spec {:spec (s/nilable ::name)
                                 :swagger/example "unc-22"
                                 :description "The CGC name."}))

(s/def :gene/sequence-name (stc/spec {:spec (s/nilable ::name)
                                      :swagger/example "AAH1.1"
                                      :description "The sequence name of the Gene."}))

(s/def :gene/biotype (stc/spec {:spec ::wsb/identifier
                                :swagger/example "cds"
                                :description "The biotype of gene."}))

(s/def :gene/species (stc/spec {:spec ::wss/identifier
                                :swagger/example "Caenorhabditis elegans"
                                :description "The species associated with the Gene."}))


(s/def :gene/status (stc/spec {:spec string?
                               :swagger/example "live"
                               :description "The status of the Gene."}))

(s/def ::anonymous (stc/spec (s/keys :req-un [:gene/id
                                              :gene/species
                                              :gene/status])))

(s/def ::cloned (stc/spec
                 (stc/merge
                  (s/keys :req-un [:gene/biotype
                                   :gene/sequence-name
                                   :gene/species]
                          :opt-un [:gene/cgc-name
                                   :gene/status]))))

(s/def ::uncloned (stc/spec
                   (stc/merge
                    (s/keys :req-un [:gene/cgc-name]
                            :opt-un [:gene/species
                                     :gene/status]))))

(def not-empty? #(not (empty? %)))

(s/def ::new (stc/spec {:spec (s/and not-empty?
                                     (s/or :cloned ::cloned
                                           :uncloned ::uncloned))
                        :description (str "The data required to populate a new Gene. "
                                          "This data should be in one two forms: cloned, or "
                                          "uncloned.")}))
(ph/defphraser not-empty
  [_ problem]
  (str (-> problem :via last keyword name) " must not be blank."))

(ph/defphraser not-empty?
  [_ problems]
  (str "A new gene requires at least a species and a name."))

;; (ph/defphraser #(contains? % :cgc-name) [_ _] "cgc-name is required")
;; (ph/defphraser #(contains? % :sequence-name) [_ _] "sequence-name is required")
;; (ph/defphraser #(contains? % :biotype) [_ _] "biotype is required")


(s/def ::new-unnamed (s/keys :req-un [:gene/id
                                      :gene/species]))

(s/def ::created (stc/spec {:spec (s/keys :req-un [:gene/id])
                            :description "The response data return from creating a new Gene."}))

(s/def ::update (stc/spec {:spec (s/and (s/keys :opt-un [:gene/cgc-name
                                                         :gene/sequence-name
                                                         :gene/biotype
                                                         :gene/species])
                                        seq)
                           :description "The data requried to update a Gene."}))

(s/def ::updated (stc/spec {:spec (s/keys :req-un [:gene/id])
                            :description "The response data from updating a Gene."}))

(s/def ::merge (stc/spec {:spec (s/keys :req-un [:gene/biotype])
                          :description "The data requried to update a Gene."}))

(s/def ::product (stc/spec
                  {:spec (s/keys :req-un [:gene/sequence-name :gene/biotype])}))

(s/def ::split (stc/spec
                {:spec (s/keys :req-un [:gene/biotype ::product])
                 :description "The data required to split a Gene."}))

(s/def ::split-response (stc/spec
                         {:spec (s/keys :req-un [::updated ::created])
                          :description "The resposne data from splitting a Gene."}))

(s/def ::live :gene/id)

(s/def ::dead :gene/id)

(s/def ::undone (stc/spec (s/keys :req-un [::live ::dead])))

(s/def ::status-changed (stc/spec (s/keys :req-un [:gene/status])))

(s/def ::summary (stc/spec {:spec (s/merge (s/keys :req-un [::wsp/history])
                                           (s/or :cloned ::cloned
                                                 :uncloned ::uncloned
                                                 :anonymous ::anonymous))
                            :description "The data associated with a stored Gene."}))

(s/def ::identifier (stc/spec {:spec (s/or :gene/id :gene/id
                                           :gene/cgc-name :gene/cgc-name
                                           :gene/sequence-name :gene/sequence-name)
                               :description "An identifier which uniquely identifies a Gene."}))


(s/def ::cgc-names (stc/spec (s/coll-of (s/keys :req-un [:gene/cgc-name]) :min-count 1)))

(s/def ::new-batch (stc/spec
                    {:spec (s/coll-of ::new :min-count 1)
                     :description "A collection of mapppings describing the genes to be created."}))

(s/def ::update-batch (stc/spec
                       {:spec (s/coll-of
                               (s/merge
                                (s/keys :req-un [:gene/id]) ::update)
                               :min-count 1)
                        :description "A collection of mappings describing the genes to be updated."}))

(s/def ::change-status-batch (stc/spec {:spec (s/coll-of (s/keys :req-un [(or :gene/id
                                                                              :gene/cgc-name
                                                                              :gene/sequence-name)])
                                                         :min-count 1)
                                        :description "A collection of one or more identifiers."}))
(s/def ::kill-batch ::change-status-batch)
(s/def ::suppress-batch ::change-status-batch)
(s/def ::resurrect-batch ::change-status-batch)

(s/def ::batch-merge-item (s/tuple
                           (s/or :gene/id :gene/id)
                           (s/or :gene/id :gene/id)
                           (s/or :gene/biotype :gene/biotype)))

(s/def ::from-gene (s/or :gene/id :gene/id))
(s/def ::into-gene (s/or :gene/id :gene/id))
(s/def ::into-biotype :gene/biotype)
(s/def ::batch-merge-item (s/keys :req-un [::from-gene ::into-gene ::into-biotype]))
(s/def ::merge-gene-batch (stc/spec
                           {:spec (s/coll-of ::batch-merge-item :min-count 1)
                            :description "A collection of mapping describing the genes to be merged."}))

(s/def ::from-id (s/or :gene/id :gene/id))
(s/def ::new-biotype (s/nilable :gene/biotype))
(s/def ::product-biotype :gene/biotype)
(s/def ::product-sequence-name :gene/sequence-name)
(s/def ::batch-split-item (s/keys :req-un [::from-id ::new-biotype ::product-sequence-name ::product-biotype]))
(s/def ::split-gene-batch (stc/spec
                           {:spec (s/coll-of ::batch-split-item :min-count 1)
                            :description "A collection of mappings describing the genes to be split."}))


(s/def ::find-match (stc/spec {:spec (s/keys :req-un [:gene/id]
                                             :opt-un [:gene/cgc-name
                                                      :gene/sequence-name])
                               :description "A mappings describing a search result match."}))
(s/def ::matches (stc/spec {:spec (s/coll-of ::find-match :kind vector?)
                            :description "A collection of search result matches."}))
(s/def ::find-result (stc/spec {:spec (s/keys :req-un [::matches])
                                :description "The result of a search."}))
