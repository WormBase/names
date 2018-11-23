(ns wormbase.specs.species
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]))

(def id-regexp #"[a-z]{1}-[a-z]+$")

(def latin-name-regexp #"[A-Z]{1}[a-z]+\s{1}[a-z]+$")

(s/def :species/id  (stc/spec
                     (s/and sts/keyword?
                            #(= (namespace %) "species")
                            #(re-matches id-regexp (name %)))))

(s/def :species/latin-name (stc/spec
                            (s/and sts/string? #(re-matches latin-name-regexp %))))

(s/def ::identifier (stc/spec (s/or :species/id :species/id
                                    :species/latin-name :species/latin-name)))
