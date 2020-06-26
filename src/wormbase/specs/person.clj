(ns wormbase.specs.person
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]))

(def email-regexp #".*@wormbase\.org")

(def id-regexp #"^WBPerson\d{1,}")

(s/def ::id (stc/spec {:spec (s/and sts/string? #(re-matches id-regexp %))
                       :swagger/example "WBPerson33035"
                       :description "The WBPerson identfiier associated with the person."}))

(s/def ::name (stc/spec {:spec (s/and sts/string? not-empty)
                         :swagger/example "Joe Bloggs"
                         :description "The name of the WormBase person as known to the names service."}))

(s/def :person/id ::id)

(s/def :person/name ::name)

(s/def :person/active? sts/boolean?)

(s/def ::email (stc/spec {:spec (s/and sts/string? #(re-matches email-regexp %))
                          :swagger/example "some-name@wormbase.org"
                          :description "The Google email address of the associated WormBase person."}))

(s/def :person/email ::email)

(s/def ::identified (stc/spec (s/keys :req-un [::name :person/email])))

(s/def ::identifier (stc/spec {:spec (s/or :person/email ::email
                                           :person/id ::id)
                               :swagger/example "some.name@wormbase.org"
                               :description "An identifier uniquely identifing a WormBase person."}))

(def example-summary {:person-email "some-user@wormbas.eorg"
                      :person/id "WBPerson11111007"
                      :person/active? true
                      :person/name "Test User"})

(s/def ::summary (stc/spec {:spec (s/keys :req-un [:person/email :person/id]
                                          :opt-un [:person/active?
                                                   :person/name])
                            :swagger/example example-summary}))

(s/def ::people (stc/spec {:spec (s/coll-of ::summary :kind sts/vector? :min-count 1)}))

(s/def ::update (stc/spec {:spec (s/keys :opt-un [:person/active?
                                                  :person/email
                                                  :person/id
                                                  :person/name])
                           :swagger/example example-summary}))

(s/def ::created ::summary)


