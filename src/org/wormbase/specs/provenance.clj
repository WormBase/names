(ns org.wormbase.specs.provenance
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [org.wormbase.specs.user :as ows-user]))

;; clients should provide zoned-date-time
;; db wants instant.
(s/def :provenance/when inst?)

(s/def :provenance/client #{:web :script})

(s/def :provenance/who ::ows-user/user)

(s/def :provenance/how #{:script :web-form :import})

(s/def :provenance/why (s/and string? (complement str/blank?)))

;; Cardinality-one reference to a gene attribute that was merged from.
(s/def :provenance/merged-from (s/keys :req [:gene/id]))

(def db-specs [:provenance/how
               :provenance/when
               :provenance/who
               :provenance/why
               :provenance/merged-from])
