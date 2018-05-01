(ns wormbase.specs.person
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]))

(def email-regexp #"[a-z0-9][a-z0-9.]+?@wormbase\.org")

(def id-regexp #"^WBPerson\d{1,}")

(s/def ::id (stc/spec (s/and sts/string? #(re-matches id-regexp %))))

(s/def ::name (stc/spec (s/and sts/string? not-empty)))

(s/def :person/name ::name)

(s/def :person/active? sts/boolean?)

(s/def ::email (stc/spec (s/and sts/string? #(re-matches email-regexp %))))

(s/def :person/email ::email)

(s/def :person/id ::id)

(s/def ::permissions (stc/spec (s/coll-of ::permission
                                          :kind sts/vector?
                                          :min-count 1)))

(s/def ::role (stc/spec (s/and sts/keyword? #(= (namespace %) "person.role"))))

(s/def :role/id (stc/spec (s/keys :req [::role])))


(s/def :person/roles (stc/spec (s/coll-of ::role :distinct true)))

(s/def ::people (stc/spec (s/coll-of ::person :kind sts/vector? :min-count 1)))

(s/def ::identified (stc/spec (s/keys :req-un [::name :person/email])))

(s/def ::identifier (stc/spec (s/or :person/email ::email
                                    :person/id ::id)))
 
(s/def ::person (stc/spec (s/keys :req [:person/email :person/id]
                                  :opt [:person/roles
                                        :person/active?
                                        :person/name])))
(s/def ::created ::person)

