(ns wormbase.specs.provenance
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [wormbase.specs.person] ;; for specs
   [wormbase.specs.agent :as owsa]))

(s/def :agent/id string?)

;; TODO: clients should provide zoned-date-time (times in UTC)
;;       - db wants instants (java.utl.Date values)

(s/def :provenance/when inst?)

(s/def :provenance/who (s/keys :req [(or :person/id :person/email)]))

(s/def :provenance/how (s/keys :req-un [::owsa/agent]))

(s/def :provenance/why (s/and string? (complement str/blank?)))

(s/def :provenance/merged-from (s/keys :req [:gene/id]))
