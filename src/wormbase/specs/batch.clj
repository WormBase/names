(ns wormbase.specs.batch
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [wormbase.specs.provenance] ;; side effects
   [wormbase.specs.gene :as wsg]))

(s/def :batch/id uuid?)

(s/def ::size pos-int?)

(s/def ::success-response (stc/spec (s/keys :req [:batch/id])))

(s/def ::created ::success-response)

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
(s/def ::merged ::success-response)


(s/def ::split-into :gene/id)
(s/def ::split-from :gene/id)
(s/def ::split-item (s/keys :req-un [::split-into ::split-from]))
(s/def ::split (stc/spec (s/coll-of ::split-item :min-count 1)))
