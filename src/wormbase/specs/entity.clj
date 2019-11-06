(ns wormbase.specs.entity
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [spec-tools.spec :as sts]
   [spec-tools.core :as stc]
   [wormbase.specs.provenance :as wsp]))

(s/def ::id-template (stc/spec
                      {:spec (s/and string?
                                    #(str/starts-with? % "WB")
                                    #(str/includes? % "%"))
                       :swagger/example "WBGene%08d"
                       :description (str "A sprintf-style format string "
                                         "that will be used for generating identifiers.")}))

(s/def ::generic? (stc/spec {:spec sts/boolean?
                             :description "True if the entity is generic."}))

(s/def ::name-required? (stc/spec {:spec sts/boolean?
                                   :description "True if the entity is requried to hold a name."}))

(s/def ::entity-type (stc/spec {:spec (s/and string? #(not (empty %)))
                                :swagger/example "variation"
                                :description "The name of the entity type."}))

(s/def ::new-schema (stc/spec {:spec (s/keys ::req-un [::id-template
                                                       ::entity-type
                                                       ::generic?
                                                       ::name-required?])
                               :description "Parameters required to install a new entity schema."}))

(s/def ::named? ::name-required?)

(s/def ::schema-list-item (s/keys :req-un [::enabled? ::generic? ::named?]))

(s/def ::schema-listing (stc/spec {:spec (s/coll-of ::schema-list-item :min-count 1)}))

(s/def ::id (stc/spec {:spec (s/and string?
                                    #(str/starts-with? % "WB"))
                       :description "An entity identifier."
                       :swagger/example "WBVar12345678"}))

(s/def ::name (stc/spec {:spec (s/nilable
                                (s/and string?
                                       (partial re-seq #"^[A-Za-z]")
                                       #(not (str/includes? % " "))))
                         :description "Entity name. (AKA \"public name\""}))

(s/def ::identifier (stc/spec {:spec (s/or :id ::id
                                           :name ::name)}))

(s/def ::status (stc/spec {:spec string?
                           :swagger/example "live"
                           :description "The status of the entity."}))

(s/def ::summary (stc/spec {:spec (s/merge ::wsp/provenance
                                           (s/keys :req-un [::id ::status]
                                                   :opt-un [::name]))
                            :description "A summary of the information held for a given entity."}))

(s/def ::status-changed (stc/spec {:spec (s/keys :req-un [::status])}))

(s/def ::new (stc/spec {:spec (s/keys :req-un [::name])
                        :description "The data requried to store a new entity."}))

(s/def ::update (stc/spec {:spec ::new
                           :description "The data required to update a entity."}))

(s/def ::new-batch (stc/spec
                    {:spec (s/coll-of ::new :min-count 1)
                     :description "A collection of mappings specifying new entitys to be created.."}))

(s/def ::update-batch (stc/spec
                       {:spec (s/coll-of
                               (s/merge ::update (s/keys :req-un [::id]))
                               :min-count 1)
                        :description "A collection of mappings specifying entitys to be updated."}))

(s/def ::change-status-batch (stc/spec {:spec (s/coll-of ::identifier :min-count 1)
                                        :description (str "A collection of identifiers respsenting "
                                                          "variaitons that should have their "
                                                          "status changed.")}))
(s/def ::kill-batch ::change-status-batch)
(s/def ::resurrect-batch ::change-status-batch)

(s/def ::find-match (stc/spec (s/keys :req-un [(or ::id ::name)])))
(s/def ::matches (stc/spec (s/coll-of ::find-match :kind vector?)))
(s/def ::find-result (stc/spec (s/keys :req-un [::matches])))

(s/def ::created (stc/spec {:spec (s/keys :req-un [::id])
                            :description "A mapping describing a newly created entity."}))

(s/def ::names (stc/spec {:spec (s/coll-of (s/or :name ::name) :min-count 1)
                          :description "A collection of entity names."}))




