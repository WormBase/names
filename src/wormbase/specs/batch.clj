(ns wormbase.specs.batch
  (:require
   [clojure.spec.alpha :as s]
   [datomic.api :as d]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]
   [wormbase.specs.gene :as wsg]
   [wormbase.specs.provenance :as wsp]))

(s/def :batch/id (stc/spec {:spec uuid?
                            :swagger/example (d/squuid)
                            :description "Unique identifier."}))

(s/def ::size pos-int?)

(s/def ::success-response  (s/keys :req-un [:batch/id]))

(s/def ::ids (s/and (s/coll-of map?)))

(s/def ::created (s/merge ::success-response (s/keys :req-un [::ids])))

(s/def ::updated ::success-response)

(s/def ::status-change (stc/spec (s/nilable (s/keys :opt-un [:provenance/why
                                                             :provenance/when
                                                             :provenance/who]))))
(s/def ::status-change (stc/spec (s/coll-of ::status-change :min-count 1)))

(s/def ::status-changed (stc/spec (s/map-of (s/and keyword #{:dead :live :suppressed :retracted})
                                            ::success-response)))

(s/def ::merge-into :gene/id)
(s/def ::merge-from :gene/id)

(s/def ::merge-item (s/keys :req-un [::merge-into ::merge-from]))
(s/def ::merge (stc/spec (s/coll-of ::merge-item :min-count 1)))


(s/def ::split-into :gene/id)
(s/def ::split-from :gene/id)
(s/def ::split-item (s/keys :req-un [::split-into ::split-from]))
(s/def ::split (stc/spec (s/coll-of ::split-item :min-count 1)))

(s/def ::summary (s/merge ::wsp/provenance ::success-response))


