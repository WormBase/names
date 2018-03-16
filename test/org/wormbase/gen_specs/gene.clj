(ns org.wormbase.gen-specs.gene
  (:require
   [clojure.java.io :as io]
   [clojure.test :as t]
   [clojure.spec.alpha :as s]
   [miner.strgen :as sg]
   [org.wormbase.db.schema :as owdbs]
   [org.wormbase.specs.gene :as owsg]
   [org.wormbase.specs.species]
   [org.wormbase.gen-specs.util :as util]
   [org.wormbase.gen-specs.person :as owgs-person]
   [org.wormbase.gen-specs.species :as gss])
  (:refer-clojure :exclude [update]))

(def id (s/gen :gene/id
               {:gene/id #(sg/string-generator owsg/gene-id-regexp)}))

(def biotype (s/gen :gene/biotype
                    {:gene/biotype #(s/gen (->> (util/load-enum-samples "biotype")
                                                (map :db/ident)
                                                set))}))


(def species (s/gen :gene/species
                    (->> (util/load-seed-data)
                         (filter :species/id)
                         (map :species/id)
                         (map (partial array-map :species/id))
                         set)))

(s/def ::species
  (s/with-gen
    :gene/species
    #(s/gen (->> (util/load-seed-data)
                (filter :species/id)
                (map :species/id)
                (map (partial array-map :species/id))
                set))))

(def update (s/gen ::owsg/update
                   {:gene/biotype (constantly biotype)
                    :gene/species (constantly (s/gen ::species))
                    :gene/cgc-name (constantly (s/gen :gene/cgc-name))
                    :gene/sequence-name (constantly (s/gen :gene/sequence-name))
                    [:provenance/who :person/email] (constantly owgs-person/email)
                    [:provenance/who :person/roles] (constantly owgs-person/roles)}))
