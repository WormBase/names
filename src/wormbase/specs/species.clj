(ns wormbase.specs.species
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]
   [clojure.string :as str]))

(def id-regexp #"[a-z]{1}-[a-z]+$")

(def latin-name-regexp #"[A-Z]{1}[a-z]+ [a-z]+$")

(s/def :species/cgc-name-pattern (s/and string?
                                        (complement str/blank?)))

(s/def :species/sequence-name-pattern (s/and string?
                                             (complement str/blank?)))

(s/def :species/id (stc/spec
                    (s/and sts/keyword?
                           #(= (namespace %) "species")
                           #(re-matches id-regexp (name %)))))

(s/def ::species-id-name (stc/spec
                          (s/and string?
                                 #(str/includes? % "-"))))

(s/def :species/latin-name (stc/spec
                            (s/and sts/string? #(re-matches latin-name-regexp %))))

(s/def ::identifier (stc/spec (s/or :species/id :species/id
                                    :species/latin-name :species/latin-name)))

(s/def ::new (stc/spec
              (s/keys :req [:species/latin-name
                            :species/cgc-name-pattern
                            :species/sequence-name-pattern])))
(s/def ::update ::new)
