(ns wormbase.specs.batch
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [wormbase.specs.provenance] ;; side effects
   [wormbase.specs.gene :as wsg]))

;; self-reminder: s/or's allow for expansion to other entity types
;; cannot factor out these repetetive s/or expressions easily as it's
;; a macro in current version of spec :/

(s/def ::success-response (stc/spec (s/keys :req [:batch/id])))

(s/def ::created ::success-response)

(s/def ::new (stc/spec (s/coll-of ::wsg/new :min-count 1)))
(s/def ::update (stc/spec (s/coll-of ::wsg/update :min-count 1)))


(s/def ::status-change (stc/spec (s/nilable (s/keys :opt [:provenance/why
                                                          :provenance/when
                                                          :provenance/who]))))
(s/def ::status-change (stc/spec (s/coll-of ::status-change :min-count 1)))

(s/def ::merge-into :gene/id)
(s/def ::merge-from :gene/id)
(s/def ::merge-item (s/merge (s/keys :req-un [::merge-into ::merge-from]) ::merge))
(s/def ::merge (stc/spec (s/coll-of ::merge-item :min-count 1)))

(s/def ::split-into :gene/id)
(s/def ::split-from :gene/id)
(s/def ::split-item (s/merge (s/keys :req-un [::split-into ::split-from]) ::split))
(s/def ::split (stc/spec (s/coll-of ::split-item :min-count 1)))

(s/def ::name-attr (stc/spec (s/or :gene-attr ::wsg/name-attr)))
(s/def ::remove-names (stc/spec (s/coll-of ::wsg/remove-name-item :min-count 1)))

(s/def ::change-status (stc/spec (s/coll-of :wsg/identfier :min-count 1)))
(s/def ::kill ::change-status)
(s/def ::suppresss ::change-status)
(s/def ::resurrect ::change-status)


