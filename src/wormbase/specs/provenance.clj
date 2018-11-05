(ns wormbase.specs.provenance
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [wormbase.specs.person] ;; side effecting: to bring in specs
   [wormbase.specs.agent :as wsa]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]))

;; TODO: clients should provide zoned-date-time (times in UTC)
;;       - db wants instants (java.utl.Date values)

(s/def :provenance/when sts/inst?)

(s/def :provenance/what sts/keyword?)

(s/def :provenance/who (stc/spec (s/keys :req [(or :person/id :person/email)])))

(s/def :provenance/how ::wsa/id)

(s/def :provenance/why (stc/spec (s/and sts/string? (complement str/blank?))))

(s/def ::gene-ref (stc/spec (s/keys :req [:gene/id])))

(s/def :provenance/merged-from ::gene-ref)

(s/def :provenance/merged-into ::gene-ref)

(s/def :provenance/split-from ::gene-ref)

(s/def :provenance/split-into ::gene-ref)

(s/def ::provenance (s/keys :opt [:provenance/how
                                  :provenance/what
                                  :provenance/when
                                  :provenance/why
                                  :provenance/merged-from
                                  :provenance/merged-into
                                  :provenance/split-from
                                  :provenance/split-into]))
