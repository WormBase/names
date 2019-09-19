(ns wormbase.specs.entity
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [spec-tools.spec :as sts]
   [spec-tools.core :as stc]
   [wormbase.specs.provenance :as wsp]))

(s/def ::id-prefix (stc/spec {:spec (s/and string? #(str/starts-with? % "WB"))
                              :swagger/example "WBGene"
                              :description "The string that will be the prefix for all primary identifiers."}))

(s/def ::type-name (stc/spec {:spec (s/and string? #(every?
                                                     (fn [^Character ch]
                                                       (and
                                                        (Character/isLetter ch)
                                                        (Character/isLowerCase ch))) %))
                              :swagger/example "'variation'"
                              :description "The name of the entity type."}))

(s/def ::id qualified-keyword?)

(s/def ::name (stc/spec {:spec (s/and string?
                                      (partial re-seq "^[A-Za-z]")
                                      #(not (str/includes? % " ")))
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

(s/def ::new-batch (stc/spec {:spec (s/coll-of ::new :min-count 1)
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




