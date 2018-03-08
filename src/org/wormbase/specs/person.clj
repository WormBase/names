(ns org.wormbase.specs.person
  (:require [clojure.spec.alpha :as s]
            [miner.strgen :as sg])
  (:import (java.util UUID)))

;; TODO: generators for these specs.

(s/def ::name (s/and string? not-empty))

(def email-regexp #"[a-z0-9][a-z0-9.]+?@wormbase\.org")

(s/def :person/email
  (s/with-gen
    (s/and string? #(re-matches email-regexp %))
    #(sg/string-generator email-regexp)))


;; Permissions
(s/def ::available-permissions #{:view
                                 :kill
                                 :add-name
                                 :override-nomenclature})

(s/def ::permission (s/with-gen
                      (s/and keyword? ::available-permissions)
                      #(s/gen ::available-permissions)))

(s/def ::permissions (s/coll-of ::permission :kind set? :min-count 1))

; ; Roles
(s/def ::available-roles #{:admin :staff :public})

(s/def ::role (s/with-gen
                (s/and keyword? ::available-roles)
                #(s/gen ::available-roles)))

(s/def :role/id (s/keys :req [::role]))

(s/def :person/roles (s/every ::role :into set :kind set? :distinct true))

;; Collections
(s/def ::new (s/keys :req [:person/email :person/roles]))

(s/def ::person (s/keys :req [:person/email]
                        :opt [:person/roles]))

(s/def ::people (s/coll-of ::person :kind vector? :min-count 1))

(s/def ::created (s/keys :req [::people]))

(s/def ::identified (s/keys :req-un [::name :person/email]))
