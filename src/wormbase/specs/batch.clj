(ns wormbase.specs.batch
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [wormbase.specs.provenance] ;; side effects
   [wormbase.specs.gene :as wsg]
   [spec-tools.spec :as sts]))

(s/def :batch/id uuid?)

(s/def ::size pos-int?)

(s/def ::success-response (stc/spec (s/keys :req [:batch/id])))

(s/def ::identifier-key sts/keyword?)
(s/def ::identifiier-values (s/coll-of string? :min-count 1))
(s/def ::identifiers (s/keys :req-un [::identifier-key ::identifier-values]))

(s/def ::created (s/merge ::success-response (s/keys :req-un [::identifiers])))

(s/def ::new (stc/spec (s/coll-of ::wsg/new :min-count 1)))

(s/def ::update (stc/spec (s/coll-of ::wsg/update :min-count 1)))

(s/def ::updated ::success-response)

(s/def ::status-change (stc/spec (s/nilable (s/keys :opt [:provenance/why
                                                          :provenance/when
                                                          :provenance/who]))))
(s/def ::status-change (stc/spec (s/coll-of ::status-change :min-count 1)))

(s/def ::status-changed ::success-response)

(s/def ::merge-into :gene/id)
(s/def ::merge-from :gene/id)

(s/def ::merge-item (s/keys :req-un [::merge-into ::merge-from]))
(s/def ::merge (stc/spec (s/coll-of ::merge-item :min-count 1)))


(s/def ::split-into :gene/id)
(s/def ::split-from :gene/id)
(s/def ::split-item (s/keys :req-un [::split-into ::split-from]))
(s/def ::split (stc/spec (s/coll-of ::split-item :min-count 1)))
