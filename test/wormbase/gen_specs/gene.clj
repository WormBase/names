(ns wormbase.gen-specs.gene
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [miner.strgen :as sg]
   [wormbase.specs.agent :as wna]
   [wormbase.specs.gene :as wsg]
   [wormbase.specs.species]
   [wormbase.gen-specs.util :as util]
   [clojure.string :as str])
  (:refer-clojure :exclude [identity update]))

(s/def ::non-blank-string (s/and string? (complement str/blank?)))

(defn species-vals [species-kw]
  (->> (util/load-seed-data)
       (filter species-kw)
       (map species-kw)
       (map name)
       set))

(def id (sg/string-generator wsg/gene-id-regexp))

(def biotype-overrides
  {:gene/biotype #(s/gen (->> (util/load-enum-samples "biotype")
                              (map :db/ident)
                              (map name)
                              set))})

(def biotype (s/gen :gene/biotype biotype-overrides))


(def all-statuses #{"dead" "live" "suppressed"})

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

(defn gen-other-names
  [n]
  (gen/sample (s/gen ::non-blank-string) n))

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
   :gene/species (constantly (s/gen ::species-latin-name))
   :gene/cgc-name (constantly cgc-name)
   :gene/id (constantly id)
   :gene/status (constantly status)
   :gene/sequence-name (constantly sequence-name)})

(def summary (s/gen ::wsg/summary overrides))

(def cloned (s/gen ::wsg/cloned overrides))

(def uncloned (s/gen ::wsg/uncloned overrides))

(def payload (gen/one-of [cloned uncloned]))
