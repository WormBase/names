(ns wormbase.specs.gene
  (:require
   [clojure.string :as str]
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]
   ;; load these namespaces for spec registration
   [wormbase.specs.provenance]
   [wormbase.specs.species]))


(def gene-id-regexp #"WBGene\d{8}")

(s/def ::new-name (stc/spec (s/and string? not-empty)))

(s/def ::existing-name (stc/spec string?))

(s/def :gene/id (stc/spec
                 (s/and string?
                        (partial re-matches gene-id-regexp))))

(s/def :gene/cgc-name (s/nilable ::new-name))

(s/def :gene/sequence-name ::new-name)

(s/def :gene/biotype sts/keyword?)

(s/def :gene/species (stc/spec
                      (s/keys
                       :req [(or :species/id :species/latin-name)])))

(s/def :gene.status/dead sts/boolean?)

(s/def :gene.status/live sts/boolean?)

(s/def :gene.status/suppressed sts/boolean?)

(s/def :gene/status sts/keyword?)

(s/def ::anonymous (stc/spec (s/keys :req [:gene/id
                                           :gene/species
                                           :gene/status])))

(s/def ::cloned (stc/spec
                 (stc/merge
                  (s/keys :req [:gene/biotype
                                :gene/sequence-name
                                :gene/species]
                          :opt [:gene/cgc-name
                                :gene/status
                                :provenance/when
                                :provenance/who
                                :provenance/how
                                :provenance/why])
                  (s/map-of #{:gene/biotype
                              :gene/sequence-name
                              :gene/species
                              :gene/cgc-name
                              :gene/id
                              :gene/status
                              :history
                              :provenance/when
                              :provenance/who
                              :provenance/how
                              :provenance/why}
                            any?))))

(s/def ::uncloned (stc/spec
                   (stc/merge
                    (s/keys :req [:gene/species :gene/cgc-name]
                            :opt [:gene/status
                                  :provenance/when
                                  :provenance/who
                                  :provenance/how
                                  :provenance/why])
                    (s/map-of #{:gene/species
                                :gene/cgc-name
                                :gene/status
                                :gene/id
                                :history
                                :provenance/when
                                :provenance/who
                                :provenance/how
                                :provenance/why}
                              any?))))

(s/def ::new (stc/spec (s/or :cloned ::cloned
                             :uncloned ::uncloned)))

(s/def ::new-unnamed (s/keys :req [:gene/id
                                   :gene/species]
                             :opt [:provenance/when
                                   :provenance/who
                                   :provenance/how
                                   :provenance/why]))

(s/def ::created (stc/spec (s/keys :req [:gene/id])))

(s/def ::update (stc/spec ::new)) ;; same as new

(s/def ::updated (stc/spec (s/keys :req [:gene/id])))

(s/def ::merge (stc/spec (s/keys :req [:gene/biotype])))

(s/def ::product (stc/spec
                  (s/keys :req [:gene/sequence-name :gene/biotype])))

(s/def ::split (stc/spec
                (s/keys :req [:gene/biotype]
                        :req-un [::product])))

(s/def ::split-response (stc/spec
                         (s/keys :req-un [::updated ::created])))

(s/def ::live :gene/id)

(s/def ::dead :gene/id)

(s/def ::undone (stc/spec (s/keys :req-un [::live ::dead])))

(s/def ::status-change (stc/spec (s/nilable (s/keys :opt [:provenance/why
                                                          :provenance/when
                                                          :provenance/who]))))

(s/def ::status-changed (stc/spec (s/keys :req [:gene/status])))

(s/def ::attr sts/keyword?)
(s/def ::value any?)
(s/def ::added sts/boolean?)
(s/def ::change (s/keys :req-un [::attr ::value ::added]))
(s/def ::changes (s/coll-of ::change))
(s/def ::provenance-history (s/keys :req [:provenance/how
                                          :provenance/what
                                          :provenance/when
                                          :provenance/who]
                                    :req-un [::changes]
                                    :opt [
                                          ;; most keys in the provenance
                                          ;; namespace should be in :req
                                          ;; --- *but* ----
                                          ;; if someone manually transacted at
                                          ;; the repl without going thru the
                                          ;; app, the prov might not have been
                                          ;; applied.
                                          ;; :provenance/how
                                          ;; :provenance/what
                                          ;; :provenance/when
                                          ;; :provenance/who

                                          :provenance/why
                                          :provenance/split-from
                                          :provenance/split-into
                                          :provenance/merged-from
                                          :provenance/merged-into]))
(s/def ::history (stc/spec
                  (s/coll-of ::provenance-history :type vector? :min-count 1)))


(s/def ::info (stc/spec (s/or :cloned ::cloned
                              :uncloned ::uncloned
                              :anonymous ::anonymous)))

(s/def ::identifier (s/or :gene/id :gene/id
                          :gene/cgc-name :gene/cgc-name
                          :gene/sequence-name :gene/sequence-name))

(s/def ::find-term (stc/spec (s/and string? (complement str/blank?))))

(s/def ::pattern ::find-term)

(s/def ::find-request (stc/spec (s/keys :req-un [::pattern])))

(s/def ::find-match (stc/spec (s/keys :req [:gene/id]
                                      :opt [:gene/cgc-name
                                            :gene/sequence-name])))

(s/def ::matches (stc/spec (s/coll-of ::find-match :kind vector?)))

(s/def ::find-result (stc/spec (s/keys :req-un [::matches])))


;; Batch
(s/def ::batch-new (stc/spec (s/coll-of ::new :min-count 1)))
(s/def ::batch-update (stc/spec (s/coll-of ::update :min-count 1)))
(s/def ::batch-status-change (stc/spec (s/coll-of ::status-change :min-count 1)))

(s/def ::merge-into :gene/id)
(s/def ::merge-from :gene/id)
(s/def ::merge-item (s/merge (s/keys :req-un [::merge-into ::merge-from]) ::merge))
(s/def ::batch-merge (stc/spec (s/coll-of ::merge-item :min-count 1)))

(s/def ::split-into :gene/id)
(s/def ::split-from :gene/id)
(s/def ::split-item (s/merge (s/keys :req-un [::split-into ::split-from]) ::split))


(s/def ::remove-name-item (s/keys :req [:gene/id
                                        (or :gene/cgc-name :gene/sequence-name)]))
(s/def ::batch-remove-names (stc/spec (s/coll-of ::remove-name-item :min-count 1)))

(s/def ::change-status (stc/spec (s/coll-of :wsg/identfier :min-count 1)))
(s/def ::batch-kill ::change-status)
(s/def ::batch-suppresss ::change-status)
(s/def ::batch-resurrect ::change-status)
