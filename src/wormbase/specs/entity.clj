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

(s/def ::caltech-message (stc/spec {:spec string?
                                    :swagger/example "Added 2023-11-21 22:06:46 WBVar03000001 tempVariation to obo_name_variation and obo_data_variation."
                                    :description "A message received from a caltech API call."}))

(s/def ::http-response-status-code (stc/spec {:spec (s/and int?
                                                           #(<= 100 %)
                                                           #(< % 600))
                                              :swagger/example 404
                                              :description "Any valid HTTP status code"}))

(s/def ::http-response-body (stc/spec {:spec string?
                                       :swagger/example "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n<html><head>\n<title>404 Not Found</title>\n</head><body>\n<h1>Not Found</h1>\n<p>The requested URL was not found on this server.</p>\n</body></html>\n"
                                       :description "A (complete) HTTP response body."}))

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

(s/def ::message string?)

(s/def ::new-type-response (stc/spec {:spec (s/keys :req-un [::message])
                                      :description "The response map returned by a successful entity-type creation operation."}))

(s/def ::new-type (stc/spec {:spec (s/keys :req-un [::id-template
                                                    ::entity-type
                                                    ::name-required?])
                               :description "Parameters required to install a new entity schema (aka entity type)."
                               :swagger/example {:id-template "WBThing%d"
                                                 :entity-type "thing"
                                                 :name-required? true}}))

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

(s/def ::created (stc/spec {:spec (s/keys :req-un [::id])
                            :description "A mapping describing a newly created entity."}))

(s/def ::caltech-sync (stc/spec {:spec (s/keys :req-un [::http-response-status-code]
                                               :opt-un [::caltech-message ::http-response-body])
                                 :description (str "A mapping describing the response received from "
                                                   "sending a new object to Caltech APIs (over HTTP(S)).")}))

(s/def ::created-response (stc/spec {:spec (s/keys :req-un [::created]
                                                   :opt-un [::caltech-sync])
                                     :description "The response map returned by a successful entity creation operation."}))

(s/def ::names (stc/spec {:spec (s/coll-of (s/keys :req-un [::name]) :min-count 1)
                          :description "A collection of entity names."}))




