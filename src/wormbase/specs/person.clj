(ns wormbase.specs.person
  (:require
   [clojure.spec.alpha :as s]))

(def email-regexp #"[a-z0-9][a-z0-9.]+?@wormbase\.org")

(def id-regexp #"^WBPerson\d{1,}")

(s/def ::id (s/and string? #(re-matches id-regexp %)))

(s/def ::name (s/and string? not-empty))

(s/def :person/name ::name)

(s/def :person/active? boolean?)

(s/def ::email (s/and string? #(re-matches email-regexp %)))

(s/def :person/email ::email)

(s/def :person/id ::id)

(s/def ::permissions (s/coll-of ::permission :kind set? :min-count 1))

(s/def ::role (s/and keyword? #(= (namespace %) "person.role")))

(s/def :role/id (s/keys :req [::role]))

(s/def :person/roles (s/every ::role :into set :kind set? :distinct true))

;; (s/def ::new (s/keys :req [:person/email
;;                            :person/id]
;;                      :opt [:person/roles]))

(s/def ::people (s/coll-of ::person :kind vector? :min-count 1))

(s/def ::identified (s/keys :req-un [::name :person/email]))

(s/def ::identifier (s/or :person/email ::email
                          :person/id ::id))
 
(s/def ::person (s/keys :req [:person/email :person/id]
                        :opt [:person/roles
                              :person/active?
                              :person/name]))
(s/def ::created ::person)

