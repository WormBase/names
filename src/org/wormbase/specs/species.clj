(ns org.wormbase.specs.species
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]
            [miner.strgen :as sg]
            [spec-tools.spec :as st]))

(def id-regexp #"[a-z]{1}-[a-z]+$")

(def latin-name-regexp #"[A-Z]{1}[a-z]+\s{1}[a-z]+$") 

(s/def ::short-name
  (s/with-gen
    (s/and st/string?
           #(re-matches id-regexp %))
    #(sg/string-generator id-regexp)))

(s/def ::id 
  (s/with-gen
    (s/and st/keyword?
           #(re-matches id-regexp (name %)))
    (fn []
      (gen/fmap
       #(keyword "species" %)
       (sg/string-generator id-regexp)))))

(s/def ::latin-name
  (s/with-gen
    (s/and st/string?
           #(re-matches latin-name-regexp %))
    #(sg/string-generator latin-name-regexp)))
