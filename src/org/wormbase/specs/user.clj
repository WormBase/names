(ns org.wormbase.specs.user
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clj-uuid :as uuid]
            [spec-tools.spec :as sts]
            [miner.strgen :as sg]
            [spec-tools.spec :as st]
            [datomic.api :as d]
            [clojure.test :as t])
  (:import (java.util UUID)))

;; TODO: generators for these specs.

(s/def ::name (s/and sts/string? not-empty))

(def email-regexp #"[a-z0-9][a-z0-9.]+?@wormbase\.org")

(s/def :user/email
  (s/with-gen
    (s/and sts/string? #(re-matches email-regexp %))
    #(sg/string-generator email-regexp)))


;; Permissions
(s/def ::available-permissions #{:view
                                 :kill
                                 :add-name
                                 :override-nomenclature})

(s/def ::permission (s/with-gen
                      (s/and sts/keyword? ::available-permissions)
                      #(s/gen ::available-permissions)))

(s/def ::permissions (s/coll-of ::permission :kind set? :min-count 1))

; ; Roles
(s/def ::available-roles #{:admin :staff :public})

(s/def ::role (s/with-gen
                (s/and keyword? ::available-roles)
                #(s/gen ::available-roles)))

(s/def :role/id (s/keys :req [::role]))

(s/def :user/roles (s/every ::role :into set :kind set? :distinct true))

;; Collections
(s/def ::new (s/keys :req [:user/email :user/roles]))

(s/def ::user (s/keys :req [:user/email]
                      :opt [:user/roles]))

(s/def ::users (s/coll-of ::user :kind vector? :min-count 1))

(s/def ::created (s/keys :req [::users]))

(s/def ::identified (s/keys :req [::name :user/email]))

(def db-specs [[:user/email {:db/unique :db.unique/value}]
                :user/roles])
