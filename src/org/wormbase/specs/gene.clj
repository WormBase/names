(ns org.wormbase.specs.gene
  (:require [clojure.spec.alpha :as s]
            [spec-tools.spec :as st]
            [spec-tools.core :as stc]
            [clojure.string :as str]
            [miner.strgen :as sg]
            [clojure.test :as t]
            [org.wormbase.specs.biotype :as owsb]
            [org.wormbase.specs.species :as owss]
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
  (let [sd (:gene/species data)
        [_ species] (if (coll? sd) sd [nil sd])
        species-kw (ows/convert-to-ident species)
        name-kw* (if (namespace name-kw)
                   name-kw
                   (keyword "gene" (name name-kw)))]
    (when-let [patterns (species-kw name-patterns)]
      (if-let [name-pattern (name-kw* patterns)]
        (let [value (or (name-kw data) "")]
          (re-matches name-pattern value))))))

(defn names-valid?
  [data]
  (some (partial valid-name? data) [:gene/cgc-name :gene/sequence-name]))

(defn- gen-from-rand-name-pattern
  "Generate names for `kw` a matching random pattern."
  [kw]
  (let [species-kw (rand-nth (keys name-patterns))
        pattern (-> name-patterns species-kw kw)]
    (sg/string-generator pattern)))

(defn matches-some-pattern?
  "Returns a non-nil value if `value` for `name-kw`
  matches a pattern for any species."
  [name-kw value]
  (->> (vals name-patterns)
       (map name-kw)
       (some #(re-matches % value))))

(s/def :gene/id
  (s/with-gen
    (s/and st/string? (partial re-matches gene-id-regexp))
    #(sg/string-generator gene-id-regexp)))

(defn name-spec-with-gen [name-kw]
  (s/spec (s/and st/string? (partial matches-some-pattern? name-kw))
          :gen (partial gen-from-rand-name-pattern name-kw)))

(s/def :gene/cgc-name (name-spec-with-gen :gene/cgc-name))

(s/def :gene/sequence-name (name-spec-with-gen :gene/sequence-name))

;; TODO add proper spec
(s/def :gene/biotype (s/or :as-keyword ::owsb/id
                           :as-string ::owsb/short-name))

(s/def :gene/species (s/with-gen
                       (s/or :as-keyword ::owss/id
                             :as-string ::owss/short-name)
                       #(s/gen (-> name-patterns keys set))))

(s/def ::new-id (s/keys :req [:gene/id]))

;; Allow both cloned and un-cloned naming
;; species is *always* required
;;  * cloned genes don't neccesarily have a CGC name)
;;  * request for naming cloned gene must supply a "biotype" (class)
(s/def ::name-new (s/and (s/keys :opt [:gene/cgc-name]
                                 :req [:gene/species
                                       (or :gene/cgc-name
                                           (and :gene/sequence-name
                                                :gene/biotype))])
                         names-valid?))

(s/def ::names-new (s/coll-of ::name-new
                              :kind st/vector?
                              :min-count 1
                              :conform-keys true))

(s/def ::names-created (s/coll-of ::new-id
                                  :kind st/vector?
                                  :min-count 1
                                  :conform-keys true))

(s/def ::names-new-request (s/map-of st/keyword?
                                     ::names-new
                                     :min-count 1
                                     :max-count 1
                                     :conform-keys true))

(s/def ::name-update
  (s/and (s/keys :opt [:gene/biotype]
                 :req [:gene/id
                       :gene/species
                       (or (or :gene/cgc-name
                               :gene/sequence-name)
                           (and :gene/cgc-name
                                :gene/sequence-name))])
         names-valid?))

(s/def ::names-updated (s/map-of
                        st/keyword?
                        (s/coll-of ::name-update :kind st/vector?)))

(s/def ::names-update-request (s/map-of
                               st/keyword?
                               (s/coll-of ::name-update :kind st/map?)))

