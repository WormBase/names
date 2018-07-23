(ns wormbase.specs.gene
  (:require [clojure.spec.alpha :as s]
            ;; for specs
            [wormbase.specs.provenance]
            [wormbase.specs.species]
            [clojure.string :as str]
            [spec-tools.core :as stc]
            [spec-tools.spec :as sts]))

(def gene-id-regexp #"WBGene\d{8}")

(s/def :gene/id (stc/spec (s/and string? (partial re-matches gene-id-regexp))))

(s/def :gene/cgc-name (stc/spec (s/and string? not-empty)))

(s/def :gene/sequence-name (stc/spec (s/and string? not-empty)))

(s/def :gene/biotype (stc/spec (s/and keyword?
                                      #(= (namespace %) "biotype"))))

(s/def :gene/species (stc/spec (s/keys :req [(or :species/id :species/latin-name)])))

(s/def :gene.status/dead sts/boolean?)

(s/def :gene.status/live sts/boolean?)

(s/def :gene.status/suppressed sts/boolean?)

(def all-statuses #{:gene.status/dead
                    :gene.status/live
                    :gene.status/suppressed})

(s/def :gene/status all-statuses)

(s/def ::status (s/or :db :gene/status
                      :short (set (map (comp keyword name) all-statuses))
                      :qualified all-statuses))

(s/def ::cloned (stc/spec (s/and
                           (s/keys :req [:gene/biotype
                                         :gene/sequence-name
                                         :gene/species]
                                   :opt [:gene/cgc-name
                                         :provenance/who
                                         :provenance/how
                                         :provenance/why]))))

(s/def ::uncloned (stc/spec (s/and
                             (s/keys :req [:gene/species :gene/cgc-name]
                                     :opt [:provenance/who
                                           :provenance/how
                                           :provenance/why]))))

(s/def ::new (stc/spec (s/or :cloned ::cloned
                             :uncloned ::uncloned)))

(s/def ::new-unnamed (s/keys :req [:gene/id
                                   :gene/species]
                             :opt [:provenance/who
                                   :provenance/how
                                   :provenance/why]))

(s/def ::created (stc/spec (s/keys :req [:gene/id :gene/status])))

(s/def ::update (stc/spec (s/keys :opt [:gene/biotype
                                        :provenance/who
                                        :provenance/when
                                        :provenance/why]
                                  :req [:gene/species
                                        (or (or :gene/cgc-name
                                                :gene/sequence-name)
                                            (and :gene/cgc-name
                                                 :gene/sequence-name))])))


(s/def ::updated (stc/spec (s/keys :req [:gene/id])))

(s/def ::merge (stc/spec (s/keys :req [:gene/biotype])))

(s/def ::product (stc/spec (s/keys :req [:gene/sequence-name :gene/biotype])))

(s/def ::split (stc/spec (s/keys :req [:gene/biotype]
                                 :req-un [::product])))

(s/def ::split-response (stc/spec (s/keys :req-un [::updated ::created])))

(s/def ::live :gene/id)

(s/def ::dead :gene/id)

(s/def ::undone (s/keys :req-un [::live ::dead]))

(s/def ::kill (stc/spec (s/nilable (s/keys :opt [:provenance/why
                                                 :provenance/when
                                                 :provenance/who]))))

(s/def ::kill-response (stc/spec (s/keys :req-un [::killed])))

(s/def ::info (stc/spec (s/keys :req [:gene/id
                                      :gene/species
                                      :gene/status]
                                :opt [:gene/biotype
                                      :gene/sequence-name
                                      :gene/cgc-name])))

(s/def ::identifier (s/or :gene/id :gene/id
                          :gene/cgc-name :gene/cgc-name
                          :gene/sequence-name :gene/sequence-name))

(s/def ::killed ::identifier)

(s/def ::find-term (stc/spec (s/and string?
                                    (complement str/blank?))))

(s/def ::pattern ::find-term)

(s/def ::find-request (stc/spec (s/keys :req-un [::pattern])))

(s/def ::find-match (stc/spec (s/keys :req [:gene/id]
                                      :opt [:gene/cgc-name
                                            :gene/sequence-name])))

(s/def ::matches (stc/spec (s/coll-of ::find-match :kind vector?)))

(s/def ::find-result (stc/spec (s/keys :req-un [::matches])))


