(ns wormbase.gen-specs.variation
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [miner.strgen :as sg]
   [wormbase.specs.variation :as wsv])
  (:refer-clojure :exclude [name]))

(def id (s/gen :variation/id
               {:variation/id #(sg/string-generator wsv/id-regexp)}))

(def all-statuses #{:variation.status/dead
                    :variation.status/live
                    :variation.status/suppressed})

(def status-overrides {:variation/status (constantly (s/gen all-statuses))})

(def name (s/gen :variation/name {:variation/name #(sg/string-generator wsv/name-regexp)}))

(def payload (s/gen ::wsv/new {:variation/name (constantly name)}))
