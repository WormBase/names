(ns org.wormbase.specs.gene
  (:require [clojure.spec.alpha :as s]
            [spec-tools.spec :as st]
            [spec-tools.core :as stc]
            [clojure.string :as str]
            [miner.strgen :as sg]
            [clojure.test :as t]
            [org.wormbase.species :as ows]))

(def ^{:doc
       "Mapping of species name to name-typed valid identifier pattern."}
  name-patterns 
  {:species/c-elegans
   {:gene/cgc-name #"^[a-z21]{3,4}-[1-9]{1}\d*"
    :gene/sequence-name #"^[A-Z0-9_cel]+\.[1-9]\d{0,3}[A-Za-z]?$"}
   :species/c-briggsae
   {:gene/cgc-name #"^[a-z]{3,4}-[1-9]{1}\d*"
    :gene/sequence-name #"^CBG\d{5}$"}
   :species/c-remanei
   {:gene/cgc-name #"^[a-z]{3,4}-[1-9]{1}\d*"
    :gene/sequence-name #"^CRE\d{5}$"}
   :species/c-brenneri
   {:gene/cgc-name #"^[a-z]{3,4}-[1-9]{1}\d*"
    :gene/sequence-name #"^CBN\d{5}$"}
   :species/c-japonica
   {:gene/cgc-name #"^[a-z]{3,4}-[1-9]{1}\d*"
    :gene/sequence-name #"^CJA\d{5}$"}
   :species/p-pacificus
   {:gene/cgc-name #"^[a-z]{3,4}-[1-9]{1}\d*"
    :gene/sequence-name #"^PPA\d{5}$"}
   :species/b-malayi
   {:gene/cgc-name #"^[a-z]{3,4}-[1-9]{1}\d*"
    :gene/sequence-name #"^Bm\d+$"}
   :species/o-volvulus
   {:gene/cgc-name #"^[a-z]{3,4}-[1-9]{1}\d*"
    :gene/sequence-name #"^OVOC\d+$"}
   :species/s-ratti
   {:gene/cgc-name #"^[a-z]{3,4}-[1-9]{1}\d*"
    :gene/sequence-name #"^SRAE_[\dXM]\d+$"}})

(def gene-id-regexp #"WBGene\d{8}")

(defn valid-name?
  "Validates `name-kw` according to correspoinding species in `data`."
  [data name-kw]
  (let [species-id (some-> data :gene/species :species/id
                           )
        name-kw* (if (namespace name-kw)
                   name-kw
                   (keyword "gene" (name name-kw)))]
    (when-let [patterns (species-id name-patterns)]
      (if-let [name-pattern (name-kw* patterns)]
        (let [value (or (name-kw data) "")]
          (re-matches name-pattern value))))))

(defn names-valid?
  [data]
  (some (partial valid-name? data) [:gene/cgc-name :gene/sequence-name]))

(defn- gen-from-rand-name-pattern
  "Generate names for `kw` a matching random pattern."
  [kw]
  (let [species-kw (-> name-patterns keys rand-nth)
        pattern (-> name-patterns species-kw kw)]
    (sg/string-generator pattern)))

(defn matches-some-name?
  "Returns a non-nil value if `value` for `name-kw`
  matches a pattern for any species."
  [name-kw value]
  (->> (vals name-patterns)
       (map name-kw)
       (some #(re-matches % value))))

(defn name-spec-with-gen [name-kw]
  (s/spec (s/and string? (partial matches-some-name? name-kw))
          :gen (partial gen-from-rand-name-pattern name-kw)))

(s/def :gene/id
  (s/with-gen
    (s/and string? (partial re-matches gene-id-regexp))
    #(sg/string-generator gene-id-regexp)))

(s/def :gene/status (s/keys :req-un [:gene.status/id]))

(s/def :gene/cgc-name (name-spec-with-gen :gene/cgc-name))

(s/def :gene/sequence-name (name-spec-with-gen :gene/sequence-name))

(s/def :gene/biotype (s/keys :req [:biotype/id]))

(s/def :gene/species
  (s/with-gen
    (s/keys :req [(or :species/id :species/latin-name)])
    #(s/gen (let [species-kwds (-> name-patterns keys set)]
              (->> species-kwds
                   (interleave (repeat :species/id))
                   (partition 2)
                   (vec)
                   (map (partial apply array-map))
                   (set))))))

;; Allow both cloned and un-cloned naming
;; species is *always* required
;;  * cloned genes don't neccesarily have a CGC name)
;;  * request for naming cloned gene must supply a "biotype" (class)

;; HOW is determined by user-agent, so not an input a client should provide.
(s/def ::new (s/and (s/keys :opt [:gene/cgc-name
                                  :provenance/who
                                  :provenance/when
                                  :provenance/why]
                            :req [:gene/species
                                  (or :gene/cgc-name
                                      (and :gene/sequence-name
                                           :gene/biotype))])
                    names-valid?))

(s/def ::created (s/keys :req [:gene/id]))

(s/def ::update
  (s/and (s/keys :opt [:gene/biotype
                       :provenance/who
                       :provenance/when
                       :provenance/why]
                 :req [:gene/species
                       (or (or :gene/cgc-name
                               :gene/sequence-name)
                           (and :gene/cgc-name
                                :gene/sequence-name))])
         names-valid?))

(s/def ::updated (s/keys :req [:gene/id]))

(def db-specs [[:gene/id {:db/unique :db.unique/value}]
               [:gene/cgc-name {:db/unique :db.unique/value}]
               [:gene/sequence-name {:db/unique :db.unique/value}]
               :gene/biotype
               :gene/status
               :gene/species])
