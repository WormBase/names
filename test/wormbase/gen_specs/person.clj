(ns wormbase.gen-specs.person
  (:require
   [clojure.spec.alpha :as s]
   [miner.strgen :as sg]
   [wormbase.gen-specs.util :as util]
   [wormbase.specs.person :as wsp]))

(def email (sg/string-generator wsp/email-regexp))

(def id (sg/string-generator wsp/id-regexp))

(def active? (s/gen :person/active?
                    (constantly #{true false})))

(def person-overrides
  {[:person/id] (constantly id)
   [:person/email] (constantly email)
   [:person/active?] (constantly active?)})

(def person (s/gen ::wsp/summary person-overrides))
