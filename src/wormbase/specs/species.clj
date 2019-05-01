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

(s/def :species/id (stc/spec {:spec (s/and sts/keyword?
                                           #(= (namespace %) "species")
                                           #(re-matches id-regexp (name %)))
                              :swagger/example "species/c-elegans"
                              :description "The identifier for a species."}))

(s/def ::species-id-name (stc/spec {:spec (s/and string?
                                                 #(str/includes? % "-"))
                                    :swagger/example "c-elegans"
                                    :description "The short-name of the id."}))

(s/def :species/latin-name (stc/spec {:spec (s/and sts/string? #(re-matches latin-name-regexp %))
                                      :description "The linnaean name of the species."}))

(s/def ::identifier (stc/spec {:spec (s/or :species/id :species/id
                                           :species/latin-name :species/latin-name)}))

(s/def ::new (stc/spec
              {:spec (s/keys :req [:species/latin-name
                                   :species/cgc-name-pattern
                                   :species/sequence-name-pattern])
               :description "The data required to populate a new species."}))

(s/def ::update (stc/spec {:spec (s/and (s/keys :opt [:species/cgc-name-pattern
                                                      :species/sequence-name-pattern])
                                        seq
                                        (s/map-of (s/and sts/keyword? #{:species/sequence-name-pattern
                                                                        :species/cgc-name-pattern})
                                                  any?))
                           :description "Data required to update a species."}))
