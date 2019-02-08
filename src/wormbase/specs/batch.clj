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

(s/def ::ids (s/coll-of string? :min-count 1))
(s/def ::id-key sts/keyword?)

(s/def ::created (s/merge ::success-response (s/keys :req-un [::ids ::id-key])))

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
