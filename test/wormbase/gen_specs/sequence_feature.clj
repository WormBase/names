(ns wormbase.gen-specs.sequence-feature
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [miner.strgen :as sg]
   [wormbase.specs.sequence-feature :as wssf])
  (:refer-clojure :exclude [name]))

(def id (s/gen :sequence-feature/id
               {:sequence-feature/id #(sg/string-generator wssf/id-regexp)}))

(def all-statuses #{:sequence-feature.status/dead
                    :sequence-feature.status/live})

(def new (s/gen ::wssf/new-batch))
