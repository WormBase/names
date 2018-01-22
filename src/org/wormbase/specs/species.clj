(ns org.wormbase.specs.species
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]
            [miner.strgen :as sg]))

(def id-regexp #"[a-z]{1}-[a-z]+$")

(def latin-name-regexp #"[A-Z]{1}[a-z]+\s{1}[a-z]+$") 

;; TODO: do we need short-name now use EDN?
(s/def ::short-name
  (s/with-gen
    (s/and string?
           #(re-matches id-regexp (or % "")))
    #(sg/string-generator id-regexp)))

(s/def :species/id 
  (s/with-gen
    (s/and keyword?
           #(and (= (namespace %) "species")
                 (re-matches id-regexp (name %))))
    (fn []
      (gen/fmap
       #(keyword "species" %)
       (sg/string-generator id-regexp)))))

(s/def :species/latin-name
  (s/with-gen
    (s/and string?
           #(re-matches latin-name-regexp %))
    #(sg/string-generator latin-name-regexp)))

(def db-specs [[:species/id {:db/unique :db.unique/value}]
               [:species/latin-name {:db/unique :db.unique/value}]])
