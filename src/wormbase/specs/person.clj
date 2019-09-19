(ns wormbase.specs.person
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]))

(def email-regexp #"[a-z0-9][a-z0-9.]+?@wormbase\.org")

(def id-regexp #"^WBPerson\d{1,}")

(s/def ::id (stc/spec {:spec (s/and sts/string? #(re-matches id-regexp %))
                       :swagger/example "WBPerson33035"
                       :description "The WBPerson identfiier associated with the person."}))

(s/def ::name (stc/spec {:spec (s/and sts/string? not-empty)
                         :swagger/example "Matt Russell"
                         :description "The name of the WormBase person as known to the names service."}))

(s/def :person/name ::name)

(s/def :person/active? sts/boolean?)

(s/def ::email (stc/spec {:spec (s/and sts/string? #(re-matches email-regexp %))
                          :swagger/example "matthew.russell@wormbase.org"
                          :description "The Google email address of the associated WormBase person."}))

(s/def :person/email ::email)

(s/def :person/id ::id)

(s/def ::role string?)

(s/def :role/id (stc/spec (s/keys :req-un [::role])))


(s/def :person/roles (stc/spec (s/coll-of ::role :distinct true)))

(s/def ::identified (stc/spec (s/keys :req-un [::name :person/email])))

(s/def ::identifier (stc/spec {:spec (s/or :person/email ::email
                                           :person/id ::id)
                               :swagger/example "WBPerson33035"
                               :description "An identifier uniquely identifing a WormBase person."}))
 
(s/def ::summary (stc/spec {:spec (s/keys :req-un [:person/email
                                                   :person/id]
                                          :opt-un [:person/roles
                                                   :person/active?
                                                   :person/name])}))

(s/def ::people (stc/spec {:spec (s/coll-of ::summary :kind sts/vector? :min-count 1)}))

(s/def ::update (stc/spec (s/keys :opt-un [:person/active?
                                           :person/email
                                           :person/id
                                           :person/name
                                           :person/roles])))
(s/def ::created ::summary)


