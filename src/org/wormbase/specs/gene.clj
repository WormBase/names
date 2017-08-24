(ns org.wormbase.specs.gene
  (:require [clojure.spec.alpha :as s]
            [spec-tools.spec :as st]
            [clojure.string :as str]
            [miner.strgen :as sg]
            [clojure.test :as t]
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

(defn valid-name?
  "Validates `name-kw` according to correspoinding species in `data`."
  [data name-kw]
  (let [[_ species] (:gene/species data)
        species-kw (ows/convert-to-ident species)]
    (when-let [patterns (species-kw name-patterns)]
      (let [name-pattern (name-kw patterns)
            value (or (name-kw data) "")]
        (re-matches name-pattern value)))))

(def gene-id-regexp #"WBGene\d{8}")

(defn- gen-from-rand-name-pattern
  "Generate names for `kw` a matching random pattern."
  [kw]
  (let [species-kw (->> (keys name-patterns)
                        (random-sample 0.5)
                        (take 1)
                        (first))
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
(s/def :gene/biotype st/keyword?)

(s/def :gene/species (s/with-gen
                       (s/or
                        :as-keyword ::owss/id
                        :as-string ::owss/short-name)
                       #(s/gen (-> name-patterns keys set))))

(s/def ::new-un-cloned (s/keys :req [:gene/cgc-name
                                     :gene/species]))
(s/def ::new-cloned (s/keys
                     :req [:gene/sequence-name
                           :gene/biotype
                           :gene/species]
                     :opt [:gene/cgc-name]))

(s/def ::new-id (s/keys :req [:gene/id]))

(s/def ::new-ids (s/+ ::new-id))

(s/def ::created (s/every-kv st/keyword?
                             ::new-ids
                             :kind st/map?
                             :min-count 1
                             :max-count 1000))
(s/def ::new-name
  (s/and (s/or
          :new-un-cloned ::new-un-cloned
          :new-cloned ::new-cloned)
         ;; first argument is the spec variant:
         ;;    cloned or un-cloned
         (fn [[_ data]]
           (when-let [name-kwds (filter (partial contains? data)
                                        [:gene/cgc-name
                                         :gene/sequence-name])]
             (some (partial valid-name? data) name-kwds)))))

(s/def ::new-names (s/+ ::new-name))

(s/def ::new-names-request (s/every-kv st/keyword?
                                       ::new-names
                                       :kind st/map?))
