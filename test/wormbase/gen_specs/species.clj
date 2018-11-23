(ns wormbase.gen-specs.species
  (:require
   [clojure.spec.alpha :as s]
   [miner.strgen :as sg]
   [wormbase.gen-specs.util :as util]))

(def load-seed-data (memoize util/load-seed-data))

(defn- species-seed-data []
  (filter :species/id (load-seed-data)))

(def id (s/gen (->> (species-seed-data)
                    (map :species/id)
                    (set))))

(def latin-name (s/gen (->> (species-seed-data)
                            (map :species/latin-name)
                            (set))))

(defn- valid-name-for-species [pattern-ident species]
  (let [[ident species*] (s/conform :gene/species species)]
    (->> (species-seed-data)
         (filter #(= (ident %) species*))
         (map pattern-ident)
         (first)
         (sg/string-generator))))


(s/def ::name-patterns #{:species/cgc-name-pattern
                         :species/sequence-name-pattern})

(def cgc-name (partial valid-name-for-species :species/cgc-name-pattern))

(def sequence-name (partial valid-name-for-species :species/sequence-name-pattern))
