(ns org.wormbase.species
  (:require [clojure.spec.alpha :as s]
            [spec-tools.spec :as st]
            [clojure.string :as str]
            [org.wormbase.specs.species :as owss]))

(s/fdef latin-name->ident
        :args (s/cat :species ::owss/latin-name)
        :ret ::owss/id)
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
      (s/valid? ::owss/latin-name s)
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
      (throw (ex-info "Invalid species" {:val kw}))))
  
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
                      :id ::owss/id))
        :ret (s/nilable ::owss/id))
(defn convert-to-ident
  "Convert a string or keyword to a keyword representing a datomic ident.

  Will return nil for invalid values that cannot be converted."
  [s-or-kw]
  (-to-ident s-or-kw))

