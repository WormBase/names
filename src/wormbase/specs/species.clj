(ns wormbase.specs.species
  (:require
   [clojure.spec.alpha :as s]))

(def id-regexp #"[a-z]{1}-[a-z]+$")

(def latin-name-regexp #"[A-Z]{1}[a-z]+\s{1}[a-z]+$") 

;; TODO: do we need short-name now use EDN?
(s/def ::short-name (s/and string? #(re-matches id-regexp %)))

(s/def :species/id (s/and keyword?
                          #(and (= (namespace %) "species")
                                (re-matches id-regexp (name %)))))

(s/def :species/latin-name (s/and string? #(re-matches latin-name-regexp %)))

