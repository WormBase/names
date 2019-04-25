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
(s/def ::t sts/inst?)

;; clients are requried to provide their time zone when specifying dates.
(s/def :provenance/when (stc/spec {:spec (s/nilable wu/zoned-date-time?)
                                   :description "The date-time the curator performed the action."}))

(s/def :provenance/what sts/keyword?)

(s/def :provenance/who (stc/spec (s/keys :req [(or :person/id :person/email)])))

(s/def :provenance/how ::wsa/id)

(s/def :provenance/why (stc/spec (s/and sts/string? (complement str/blank?))))

(s/def ::provenance (stc/spec
                     (s/nilable
                      (s/keys :opt [:provenance/who
                                    :provenance/how
                                    :provenance/what
                                    :provenance/when
                                    :provenance/why]))))

(s/def ::attr sts/keyword?)

(s/def ::value any?)

(s/def ::added sts/boolean?)

(s/def ::change (s/keys :req-un [::attr ::value ::added]))

(s/def ::changes (s/coll-of ::change))

(s/def ::temporal-change (s/merge ::provenance (s/keys :req-un [::changes ::t])))

(s/def ::history (stc/spec
                  (s/coll-of ::temporal-change :type vector? :min-count 1)))
