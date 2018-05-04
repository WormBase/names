(ns wormbase.specs.provenance
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [wormbase.specs.person] ;; for specs
   [wormbase.specs.agent :as owsa]
   [spec-tools.spec :as sts]
   [spec-tools.core :as stc]))

(s/def :agent/id sts/string?)

;; TODO: clients should provide zoned-date-time (times in UTC)
;;       - db wants instants (java.utl.Date values)

(s/def :provenance/when sts/inst?)

(s/def :provenance/who (stc/spec (s/keys :req [(or :person/id :person/email)])))

(s/def :provenance/how (stc/spec (s/keys :req-un [::owsa/agent])))

(s/def :provenance/why (stc/spec (s/and sts/string? (complement str/blank?))))

(s/def :provenance/merged-from (stc/spec (s/keys :req [:gene/id])))
