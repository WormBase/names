(ns wormbase.specs.species
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]))

(def id-regexp #"[a-z]{1}-[a-z]+$")

(def latin-name-regexp #"[A-Z]{1}[a-z]+\s{1}[a-z]+$") 

;; TODO: do we need short-name now use EDN?
(s/def ::short-name (stc/spec (s/and sts/string? #(re-matches id-regexp %))))

(s/def :species/id sts/keyword?)
;; DISABLED predicate whilst attempting to resolve JSON coercion
;; #(and (= (namespace %) "species")
;;       (re-matches id-regexp (name %))))))

(s/def :species/latin-name (stc/spec
                            (s/and sts/string? #(re-matches latin-name-regexp %))))

