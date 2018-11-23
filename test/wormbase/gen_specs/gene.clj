(ns wormbase.gen-specs.gene
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [miner.strgen :as sg]
   [wormbase.db.schema :as wdbs]
   [wormbase.specs.agent :as wna]
   [wormbase.specs.gene :as wsg]
   [wormbase.specs.species]
   [wormbase.gen-specs.util :as util]
   [wormbase.gen-specs.person :as wgsp]
   [wormbase.gen-specs.species :as gss])
  (:refer-clojure :exclude [identity update]))

(defn species-vals [species-kw]
  (->> (util/load-seed-data)
       (filter species-kw)
       (map species-kw)
       set))

(def id (s/gen :gene/id
               {:gene/id #(sg/string-generator wsg/gene-id-regexp)}))

(def biotype-overrides
  {:gene/biotype #(s/gen (->> (util/load-enum-samples "biotype")
                              (map :db/ident)
                              set))})

(def biotype (s/gen :gene/biotype biotype-overrides))


(def all-statuses #{:gene.status/dead
                    :gene.status/live
                    :gene.status/suppressed})

(def status-overrides {:gene/status (constantly (s/gen all-statuses))})

(def status (s/gen :gene/status status-overrides))

(defn one-of-name-regexps [species-regexp-kw]
  (gen/one-of (->> (util/load-seed-data)
                   (filter :species/latin-name)
                   (map species-regexp-kw)
                   (map re-pattern)
                   (map sg/string-generator)
                   (vec))))

(def sequence-name (one-of-name-regexps :species/sequence-name-pattern))

(def cgc-name (one-of-name-regexps :species/cgc-name-pattern))

(s/def ::species-id
  (s/with-gen
    :gene/species
    #(s/gen (species-vals :species/id))))

(s/def ::species-latin-name
  (s/with-gen
    :gene/species
    #(s/gen (species-vals :species/latin-name))))

(def species (s/gen :gene/species
                    {:species/latin-name (constantly (s/gen ::species-latin-name))
                     :species/id (constantly (s/gen ::species-id))}))

(def identity (s/gen ::wsg/identifier
                     {:gene/id (constantly id)
                      :gene/cgc-name (constantly cgc-name)
                      :gene/sequence-name (constantly sequence-name)}))

(def overrides
  {:gene/biotype (constantly biotype)
   :species/latin-name (constantly species)
   :species/id (constantly species)
   :gene/cgc-name (constantly (s/gen :gene/cgc-name))
   :gene/id (constantly id)
   :gene/status (constantly status)
   :gene/sequence-name (constantly (s/gen :gene/sequence-name))})

(def info (s/gen ::wsg/info overrides))

(def cloned (s/gen ::wsg/cloned overrides))

(def uncloned (s/gen ::wsg/uncloned overrides))

(def payload (gen/one-of [cloned uncloned]))
