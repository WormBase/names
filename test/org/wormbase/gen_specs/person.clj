(ns org.wormbase.gen-specs.person
  (:require
   [clojure.spec.alpha :as s]
   [miner.strgen :as sg]
   [org.wormbase.gen-specs.util :as util]
   [org.wormbase.specs.person :as ows-person]
   [clojure.spec.gen.alpha :as gen]
   [org.wormbase.specs.person :as owsp]))

(def email (sg/string-generator owsp/email-regexp))

(def id (sg/string-generator owsp/id-regexp))

(def roles (s/gen :person/roles
                  {::owsp/role
                   #(s/gen
                     (->> (util/load-enum-samples "person.role")
                          (map :db/ident)
                          (set)))}))


(def person-overrides
  {[:person/id] (constantly id)
   [:person/email] (constantly email)
   [:person/roles] (constantly roles)})

(def person (s/gen ::owsp/person person-overrides))
