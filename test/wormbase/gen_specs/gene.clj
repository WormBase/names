(ns wormbase.gen-specs.gene
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [miner.strgen :as sg]
   [wormbase.db.schema :as owdbs]
   [wormbase.specs.gene :as owsg]
   [wormbase.specs.species]
   [wormbase.gen-specs.util :as util]
   [wormbase.gen-specs.person :as owgs-person]
   [wormbase.gen-specs.species :as gss])
  (:refer-clojure :exclude [identity update]))

(def id (s/gen :gene/id
               {:gene/id #(sg/string-generator owsg/gene-id-regexp)}))

(def biotype-overrides
  {:gene/biotype #(s/gen (->> (util/load-enum-samples "biotype")
                              (map :db/ident)
                              set))})

(def biotype (s/gen :gene/biotype biotype-overrides))


(def species-overrides (->> (util/load-seed-data)
                            (filter :species/id)
                            (map :species/id)
                            (map (partial array-map :species/id))
                            set))

(def species (s/gen :gene/species species-overrides))

(defn one-of-name-regexps [species-regexp-kw]
  (gen/one-of (->> (util/load-seed-data)
                   (filter :species/id)
                   (map species-regexp-kw)
                   (map re-pattern)
                   (map sg/string-generator)
                   (vec))))

(def sequence-name (one-of-name-regexps :species/sequence-name-pattern))

(def cgc-name (one-of-name-regexps :species/cgc-name-pattern))

(s/def ::species
  (s/with-gen
    :gene/species
    #(s/gen (->> (util/load-seed-data)
                 (filter :species/id)
                 (map :species/id)
                 (map (partial array-map :species/id))
                 set))))

(def identity (s/gen ::owsg/identifier
                     {:gene/id (constantly id)
                      :gene/cgc-name (constantly cgc-name)
                      :gene/sequence-name (constantly sequence-name)}))

(def update-overrides
  {:gene/biotype (constantly biotype)
   :gene/species (constantly (s/gen ::species))
   :gene/cgc-name (constantly (s/gen :gene/cgc-name))
   :gene/sequence-name (constantly (s/gen :gene/sequence-name))
   [:provenance/who :person/id] (constantly owgs-person/id)
   [:provenance/who :person/email] (constantly owgs-person/email)
   [:provenance/who :person/roles] (constantly owgs-person/roles)})

(def update (s/gen ::owsg/update update-overrides))
