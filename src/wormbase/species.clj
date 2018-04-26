(ns wormbase.species
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [wormbase.specs.species :as owss]))

(s/fdef latin-name->ident
        :args (s/cat :species :species/latin-name)
        :ret :species/id)
(defn latin-name->ident [latin-name]
  (let [[genus species] (str/split latin-name #"\s")]
    (->> [(first genus) species]
         (map str/lower-case)
         (str/join "-")
         (keyword "species"))))

(defprotocol SpeciesIdent
  (-to-ident [this]))

(extend-protocol SpeciesIdent
  String
  (-to-ident [s]
    (cond
      (s/valid? :species/latin-name s)
      (latin-name->ident s)

      (s/valid? ::owss/short-name s)
      (-to-ident (keyword "species" s))      

      s
      (throw (ex-info "Invalid species" {:val s}))))

  clojure.lang.Keyword
  (-to-ident [kw]
    (cond
      (= (namespace kw) "species")
      kw
      
      (-> kw namespace empty?)
      (keyword "species" (name kw))

      :default
      (throw (ex-info "Invalid species" {:val kw
                                         :type ::invalid}))))
  
  clojure.lang.MapEntry
  (-to-ident [me]
    (let [[x species] me]
      (-to-ident species)))

  datomic.Entity
  (-to-ident [entity]
    (:species/id entity)))


(s/fdef convert-to-ident
        :args (s/cat :s-or-kw
                     (s/or
                      :short-name ::owss/short-name
                      :id :species/id))
        :ret (s/nilable :species/id))
(defn convert-to-ident
  "Convert a string or keyword to a keyword representing a datomic ident.

  Will return nil for invalid values that cannot be converted."
  [s-or-kw]
  (-to-ident s-or-kw))

