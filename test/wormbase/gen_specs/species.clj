(ns wormbase.gen-specs.species
  (:require
   [clojure.spec.alpha :as s]
   [miner.strgen :as sg]
   [wormbase.gen-specs.util :as util]
   [wormbase.specs.species :as wss]))

(def load-seed-data (memoize util/load-seed-data))

(defn- species-seed-data []
  (filter :species/id (load-seed-data)))

(def id (s/gen (->> (species-seed-data)
                    (map :species/id)
                    (map name)
                    (set))))

(def latin-name (s/gen (->> (species-seed-data)
                            (map :species/latin-name)
                            (set))))

(defn matching-species? [ident species value]
  (when-let [iv (ident value)]
    (= (name iv) species)))

(defn- valid-name-for-species [pattern-ident species]
  (let [[ident species*] (s/conform :gene/species species)]
    (->> (species-seed-data)
         (filter (partial matching-species? ident species*))
         (map pattern-ident)
         (first)
         (sg/string-generator))))

(s/def ::name-patterns #{:species/cgc-name-pattern
                         :species/sequence-name-pattern})

(def cgc-name (partial valid-name-for-species :species/cgc-name-pattern))

(def sequence-name (partial valid-name-for-species :species/sequence-name-pattern))

(def new-latin-name (s/gen :species/latin-name
                           {:species/latin-name #(sg/string-generator wss/latin-name-regexp)}))

(def payload (s/gen ::wss/new {:species/latin-name (constantly new-latin-name)}))
