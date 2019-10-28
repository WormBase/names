(ns wormbase.specs.provenance
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [java-time :as jt]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]
   [wormbase.specs.person] ;; side effecting: to bring in specs
   [wormbase.specs.agent :as wsa]
   [wormbase.util :as wu]))

;; internal datomic tx/Instant (java.util.Date instance)
(s/def ::t (stc/spec {:spec sts/inst?
                      :description "The server-local system time when the event was proccessed."}))

;; clients are requried to provide their time zone when specifying dates.
(s/def :provenance/when (stc/spec
                         {:spec (s/nilable jt/zoned-date-time?)
                          :swagger/example (jt/format :iso-date-time
                                                      (jt/zoned-date-time (jt/instant) (jt/zone-id)))
                          :description "The date-time the curator performed the action."}))

(s/def :provenance/what  (stc/spec {:spec sts/string?
                                    :swagger/example "new-gene"
                                    :description "The type of event."}))

(s/def :provenance/who (stc/spec {:spec (s/keys :req-un [(or :person/id :person/email)])
                                  :description "The WormBase person who performed the event."}))

(s/def :provenance/how (stc/spec {:spec sts/string?
                                  :swagger/example "web"
                                  :description "The agent that was used to process the event."}))

(s/def :provenance/why (stc/spec {:spec (s/and sts/string? (complement str/blank?))
                                  :swagger/example "<express reason here>"
                                  :description "An optional string describing the reason for the event."}))

(s/def ::provenance (stc/spec
                     {:spec (s/nilable
                             (s/keys :opt-un [:provenance/who
                                              :provenance/how
                                              :provenance/what
                                              :provenance/when
                                              :provenance/why]))
                      :description "A mapping describing provenance of names service events."}))

(s/def ::attr string?)

(s/def ::value any?)

(s/def ::added sts/boolean?)

(s/def ::change (s/keys :req-un [::attr ::value ::added]))

(s/def ::changes (s/coll-of ::change))

(s/def ::temporal-change (s/merge ::provenance (s/keys :req-un [::changes ::t])))

(s/def ::history (stc/spec
                  {:spec (s/coll-of ::temporal-change :type vector? :min-count 1)
                   :description "A series of provenance events associated with a names service entity."}))
