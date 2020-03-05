(ns wormbase.specs.species
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [phrase.alpha :as ph]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]
   [wormbase.specs.provenance :as wsp]))

(def id-regexp #"[a-z]{1}-[a-z]+$")

(def latin-name-regexp #"[A-Z]{1}[a-z]+ [a-z]+$")

(s/def :species/cgc-name-pattern (s/and string?
                                        (complement str/blank?)))

(s/def :species/sequence-name-pattern (s/and string?
                                             (complement str/blank?)))

(def matches-id-regexp (partial re-matches id-regexp))

(s/def :species/id (stc/spec {:spec (s/and string? matches-id-regexp)
                              :swagger/example "species/c-elegans"
                              :description "The identifier for a species."}))

(ph/defphraser matches-id-regexp [_ _] "Species ID must match regular expression.")

(s/def ::species-id-name (stc/spec {:spec (s/and string?
                                                 #(str/includes? % "-"))
                                    :swagger/example "c-elegans"
                                    :description "The short-name of the id."}))

(def matches-latin-name-regexp (partial re-matches latin-name-regexp))

(s/def :species/latin-name (stc/spec {:spec (s/and sts/string? matches-latin-name-regexp)
                                      :description "The linnaean name of the species."}))

(ph/defphraser matches-latin-name-regexp
  [_ problem]
  "Species name must match regular expression.")


(s/def ::identifier (stc/spec {:spec (s/or :species/id :species/id
                                           :species/latin-name :species/latin-name)}))

(ph/defphraser :species/latin-name
  [_ problem]
  "Species name must match regular expression.")

(s/def ::item (s/keys :req-un [:species/id
                               :species/latin-name
                               :species/cgc-name-pattern
                               :species/sequence-name-pattern]))

(s/def ::listing (stc/spec {:spec (s/coll-of ::item :min-count 1)
                            :description "Information held about a species."}))

(s/def ::new (stc/spec
              {:spec (s/keys :req-un [:species/latin-name
                                      :species/cgc-name-pattern
                                      :species/sequence-name-pattern])
               :description "The data required to populate a new species."}))

(s/def ::created (stc/spec {:spec (s/keys :req-un [:species/latin-name])
                            :description "The response data return from creating a new Species."}))


(def allowed-to-update #{:sequence-name-pattern :cgc-name-pattern})

(s/def ::update (stc/spec {:spec (s/and
                                  (s/keys :opt-un [:species/cgc-name-pattern
                                                   :species/sequence-name-pattern])
                                  seq
                                  (s/map-of (s/and sts/keyword? allowed-to-update)
                                            any?))
                           :description "Data required to update a species."}))

(ph/defphraser allowed-to-update
  [_ prob]
  "Can only update CGC and sequence name patterns for a species.")

(s/def ::updated (stc/spec {:spec (s/keys :req-un [:species/latin-name])
                            :description "The response data from updating a Species."}))
