(ns org.wormbase.specs.gene
  (:require [clojure.spec.alpha :as s]
            ;; fpr specs
            [org.wormbase.specs.provenance]
            [org.wormbase.specs.species]))

(def gene-id-regexp #"WBGene\d{8}")

(s/def :gene/id (s/and string? (partial re-matches gene-id-regexp)))

(s/def :gene/cgc-name (s/and string? not-empty))

(s/def :gene/sequence-name (s/and string? not-empty))

(s/def :gene/biotype (s/and keyword?
                            #(= (namespace %) "biotype")))

(s/def :gene/species (s/keys :req [(or :species/id :species/latin-name)]))

(s/def :gene.status/dead boolean?)

(s/def :gene.status/live boolean?)

(s/def :gene.status/suppressed boolean?)

(def all-statuses #{:gene.status/dead
                    :gene.status/live
                    :gene.status/suppressed})

(s/def :gene/status all-statuses)

(s/def ::status (s/or :db :gene/status
                      :short (set (map (comp keyword name) all-statuses))
                      :qualified all-statuses))

(s/def ::cloned (s/and
                 (s/keys :req [:gene/biotype
                               :gene/sequence-name
                               :gene/species]
                         :opt [:gene/cgc-name
                               :provenance/who
                               :provenance/how
                               :provenance/why])))

(s/def ::uncloned (s/and
                   (s/keys :req [:gene/species :gene/cgc-name]
                           :opt [:gene/cgc-name
                                 :provenance/who
                                 :provenance/how
                                 :provenance/why])))

(s/def ::new (s/or :cloned ::cloned
                   :uncloned ::uncloned))

(s/def ::created (s/keys :req [:gene/id :gene/status]))

(s/def ::update (s/keys :opt [:gene/biotype
                              :provenance/who
                              :provenance/when
                              :provenance/why]
                        :req [:gene/species
                              (or (or :gene/cgc-name
                                      :gene/sequence-name)
                                  (and :gene/cgc-name
                                       :gene/sequence-name))]))


(s/def ::updated (s/keys :req [:gene/id]))

(s/def ::merge (s/keys :req [:gene/biotype]))

(s/def ::product (s/keys :req [:gene/sequence-name :gene/biotype]))

(s/def ::split (s/keys :req [:gene/biotype]
                       :req-un [::product]))

(s/def ::split-response (s/keys :req-un [::updated ::created]))

(s/def ::live :gene/id)

(s/def ::dead :gene/id)

(s/def ::undone (s/keys :req-un [::live ::dead]))

(s/def ::kill (s/keys :opt [:provenance/why
                            :provenance/when
                            :provenance/who]))
(s/def ::killed boolean?)

(s/def ::kill-response (s/keys :req-un [::killed]))

(s/def ::identity (s/or :gene/cgc-name :gene/cgc-name
                        :gene/sequence-name :gene/sequence-name
                        :gene/id :gene/id))
