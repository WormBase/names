(ns wormbase.specs.entity
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [phrase.alpha :as ph]
   [spec-tools.spec :as sts]
   [spec-tools.core :as stc]
   [wormbase.specs.provenance :as wsp]))

(def ^{:doc "Max number of un-named entities requestable."
       :dynamic true}
  *max-requestable-un-named* 1000000)

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
                                                       ::name-required?])
                               :description "Parameters required to install a new entity schema."
                               :swagger/example {:id-template "WBThing%d"
                                                 :entiy-type "thing"
                                                 :name-required true}}))

(s/def ::named? (stc/spec {:spec sts/boolean?
                           :swagger/example "true"
                           :description "Flag indicating if an entity type requires a name."}))

(s/def ::enabled? (stc/spec {:spec sts/boolean?
                             :swagger/example "true"
                             :description "Flag indicating if an entity type is enabled."}))

(s/def ::schema-list-item (s/keys :req-un [::enabled? ::generic? ::named?]))

(s/def ::schema-listing (stc/spec {:spec (s/coll-of ::schema-list-item :min-count 1)}))

(def is-wb-id? #(and % (str/starts-with? % "WB")))

(s/def ::id (stc/spec {:spec (s/and string? is-wb-id?)
                       :description "An entity identifier."
                       :swagger/example "WBVar12345678"}))

(ph/defphraser is-wb-id?
  [_ problem]
  (let [msg (-> problem :via last namespace)]
    {:message msg
     :code "Invalid identifier. Identifier should start with a prefix of WB."}))

;; Due to "Public_name" in the source data, the name can be any non-blank string.

(def not-blank? #(not (str/blank? %)))

(def not-start-end-space? #(not (or (str/starts-with? % " ")
                                    (str/ends-with? % " "))))

(s/def ::name (stc/spec {:spec (s/nilable (s/and string?
                                                 not-blank?
                                                 not-start-end-space?))
                         :description "Entity name. (AKA \"public name\""}))

(ph/defphraser not-blank?
  [_ problem]
  (let [ent-ns (-> problem :via last name)]
    (str ent-ns " cannot be empty.")))

(ph/defphraser not-start-end-space?
  [_ problem]
  (let [ent-ns (-> problem :via last name)]
        (str ent-ns " cannot start or end with spaces.")))

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


(s/def ::n (stc/spec {:spec (s/and pos-int? #(<= % *max-requestable-un-named*))
                      :description (str "The number of new (u-named) entiies to create. "
                                        "A number between 1 and the maximum: "
                                        *max-requestable-un-named* " .")
                      :swagger/example 100}))

(s/def ::count (s/keys :req-un [::n]))

(s/def ::new (stc/spec {:spec (s/keys :opt-un [::name])
                        :description "The data for a new entity."}))

(s/def ::update (stc/spec {:spec ::new
                           :description "The data required to update a entity."}))

(s/def ::new-batch (stc/spec
                    {:spec (s/or :un-named ::count
                                 :named (s/coll-of ::new :min-count 1))
                     :description "A collection of mappings specifying new entitys to be created.."}))

(s/def ::update-batch (stc/spec
                       {:spec (s/coll-of
                               (s/merge ::update (s/keys :req-un [::id]))
                               :min-count 1)
                        :description "A collection of mappings specifying entitys to be updated."}))

(s/def ::change-status-batch (stc/spec {:spec (s/coll-of (s/keys :req-un [(or ::name ::id)])
                                                         :min-count 1)
                                        :swagger/example "[{:id \"WBGene000000001\"}]"
                                        :description (str "A collection of mappings, "
                                                          "only one key is required")}))
(s/def ::kill-batch ::change-status-batch)
(s/def ::resurrect-batch ::change-status-batch)

(s/def ::find-match (stc/spec (s/keys :req-un [(or ::id ::name)])))
(s/def ::matches (stc/spec (s/coll-of ::find-match :kind vector?)))
(s/def ::find-result (stc/spec (s/keys :req-un [::matches])))

(s/def ::created (stc/spec {:spec (s/keys :req-un [::id])
                            :description "A mapping describing a newly created entity."}))

(s/def ::message string?)

(s/def ::schema-created (stc/spec (s/keys :req-un [::message])))

(s/def ::names (stc/spec {:spec (s/coll-of (s/keys :req-un [::name]) :min-count 1)
                          :description "A collection of entity names."}))




