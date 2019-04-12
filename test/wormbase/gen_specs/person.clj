(ns wormbase.gen-specs.person
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [miner.strgen :as sg]
   [wormbase.gen-specs.util :as util]
   [wormbase.specs.person :as wsp]))

(def email (sg/string-generator wsp/email-regexp))

(def id (sg/string-generator wsp/id-regexp))

(def roles (s/gen :person/roles
                  {::wsp/role
                   #(s/gen
                     (->> (util/load-enum-samples "person.role")
                          (map :db/ident)
                          (set)))}))

(def person-overrides
  {[:person/id] (constantly id)
   [:person/email] (constantly email)
   [:person/roles] (constantly roles)})

(def person (s/gen ::wsp/summary person-overrides))
