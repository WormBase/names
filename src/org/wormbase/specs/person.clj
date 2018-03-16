(ns org.wormbase.specs.person
  (:require [clojure.spec.alpha :as s]
            [miner.strgen :as sg])
  (:import (java.util UUID)))

(def email-regexp #"[a-z0-9][a-z0-9.]+?@wormbase\.org")

(s/def ::name (s/and string? not-empty))

(s/def :person/email (s/and string? #(re-matches email-regexp %)))

(s/def ::permissions (s/coll-of ::permission :kind set? :min-count 1))

(s/def ::role (s/and keyword? #(= (namespace %) "person.role")))

(s/def :role/id (s/keys :req [::role]))

(s/def :person/roles (s/every ::role :into set :kind set? :distinct true))

(s/def ::new (s/keys :req [:person/email :person/roles]))

(s/def ::person (s/keys :req [:person/email]
                        :opt [:person/roles]))

(s/def ::people (s/coll-of ::person :kind vector? :min-count 1))

(s/def ::created (s/keys :req [::people]))

(s/def ::identified (s/keys :req-un [::name :person/email]))
