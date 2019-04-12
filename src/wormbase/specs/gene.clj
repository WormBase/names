(ns wormbase.specs.gene
  (:require
   [clojure.string :as str]
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]
   [wormbase.specs.provenance :as wsp]
   [wormbase.specs.species :as wss]))

(def gene-id-regexp #"WBGene\d{8}")

(s/def ::name (stc/spec (s/and string? not-empty)))

(s/def :gene/id ::name)

(s/def :gene/cgc-name (s/nilable ::name))

(s/def :gene/sequence-name ::name)

(s/def :gene/biotype sts/keyword?)

(s/def :gene/species ::wss/identifier)

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
                                :gene/status]))))

(s/def ::uncloned (stc/spec
                   (stc/merge
                    (s/keys :req [:gene/species :gene/cgc-name]
                            :opt [:gene/status]))))

(s/def ::new (stc/spec (s/or :cloned ::cloned
                             :uncloned ::uncloned)))

(s/def ::new-unnamed (s/keys :req [:gene/id
                                   :gene/species]))

(s/def ::created (stc/spec (s/keys :req [:gene/id])))

(s/def ::update (stc/spec (s/and (s/keys :opt [:gene/cgc-name
                                               :gene/sequence-name
                                               :gene/biotype
                                               :gene/species])
                                 seq)))

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

(s/def ::status-changed (stc/spec (s/keys :req [:gene/status])))

(s/def ::attr sts/keyword?)
(s/def ::value any?)
(s/def ::added sts/boolean?)
(s/def ::change (s/keys :req-un [::attr ::value ::added]))
(s/def ::changes (s/coll-of ::change))
(s/def ::provenance (s/merge ::wsp/provenance (s/keys :req-un [::changes ::wsp/t])))
(s/def ::history (stc/spec
                  (s/coll-of ::provenance :type vector? :min-count 1)))

(s/def ::summary (stc/spec (s/or :cloned ::cloned
                                 :uncloned ::uncloned
                                 :anonymous ::anonymous)))

(s/def ::identifier (stc/spec (s/or :gene/id :gene/id
                                    :gene/cgc-name :gene/cgc-name
                                    :gene/sequence-name :gene/sequence-name)))

(s/def ::cgc-names (stc/spec (s/coll-of (s/or :gene/cgc-name :gene/cgc-name) :min-count 1)))


(s/def ::new-batch (stc/spec (s/coll-of ::new :min-count 1)))

(s/def ::update-batch (stc/spec (s/coll-of (s/merge (s/keys :req [:gene/id]) ::update) :min-count 1)))

(s/def ::change-status-batch (stc/spec (s/coll-of ::identifier :min-count 1)))
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
(s/def ::merge-gene-batch (stc/spec (s/coll-of ::batch-merge-item :min-count 1)))

(s/def ::from-id (s/or :gene/id :gene/id))
(s/def ::new-biotype (s/nilable :gene/biotype))
(s/def ::product-biotype :gene/biotype)
(s/def ::product-sequence-name :gene/sequence-name)
(s/def ::batch-split-item (s/keys :req-un [::from-id ::new-biotype ::product-sequence-name ::product-biotype]))
(s/def ::split-gene-batch (stc/spec (s/coll-of ::batch-split-item :min-count 1)))


(s/def ::find-match (stc/spec (s/keys :req [:gene/id]
                                      :opt [:gene/cgc-name
                                            :gene/sequence-name])))
(s/def ::matches (stc/spec (s/coll-of ::find-match :kind vector?)))
(s/def ::find-result (stc/spec (s/keys :req-un [::matches])))
