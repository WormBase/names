(ns org.wormbase.specs.provenance
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [java-time :as jt]
   [org.wormbase.specs.user :as ows-user]
   [miner.strgen :as sg]))

;; clients should provide zoned-date-time
;; db wants instant.
(s/def :provenance/when inst?)

(s/def :data-source-method/script keyword?)

(s/def :provenance/who ::ows-user/user)

(s/def :provenance/how #{:script :web-form :import})

(s/def :provenance/why (s/and string? (comp not str/blank?)))

(def db-spces [:provenance/how
               :provenance/when
               :provenance/who
               :provenance/why])
