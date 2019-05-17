(ns wormbase.specs.variation
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.spec :as sts]
   [spec-tools.core :as stc]
   [wormbase.specs.provenance :as wsp]))

(def id-regexp #"WBVar\d{8}")

(def name-regexp #"(([a-z]+)(Df|Dp|Ti|T|Is)?(\d+)){1,4}")

(s/def :variation/id (stc/spec {:spec (s/and string? (partial re-matches id-regexp))
                                :swagger/example "WBVar02143829"
                                :description "A unique identifier."}))

(s/def :variation/name (stc/spec {:spec (s/and string?
                                               (partial re-matches name-regexp))
                                  :swagger/example "lax1"
                                  :description "The name of the variation."}))

(s/def :variation/status (stc/spec {:spec sts/keyword?
                                    :swagger/example "variation.status/live"
                                    :description "The status of the variation."}))

(s/def ::identifier (stc/spec {:spec (s/or :variation/id :variation/id
                                           :variation/name :variation/name)
                               :description "An string uniquely idnetifying a variation."}))

(s/def ::summary (stc/spec {:spec (s/merge ::wsp/provenance
                                           (s/keys :req [:variation/id :variation/status]
                                                   :opt [:variation/name]))
                            :description "A summary of the information held for a given variation."}))

(s/def ::status-changed (stc/spec {:spec (s/keys :req [:variation/status])}))

(s/def ::new (stc/spec {:spec (s/keys :req [:variation/name])
                        :description "The data requried to store a new variation."}))

(s/def ::update (stc/spec {:spec ::new
                           :description "The data required to update a variation."}))

(s/def ::new-batch (stc/spec {:spec (s/coll-of ::new :min-count 1)
                              :description "A collection of mappings specifying new variations to be created.."}))

(s/def ::update-batch (stc/spec
                       {:spec (s/coll-of
                               (s/merge ::update (s/keys :req [:variation/id]))
                               :min-count 1)
                        :description "A collection of mappings specifying variations to be updated."}))

(s/def ::change-status-batch (stc/spec {:spec (s/coll-of ::identifier :min-count 1)
                                        :description (str "A collection of identifiers respsenting "
                                                          "variaitons that should have their "
                                                          "status changed.")}))
(s/def ::kill-batch ::change-status-batch)
(s/def ::resurrect-batch ::change-status-batch)

(s/def ::find-match (stc/spec (s/keys :req [(or :variation/id :variation/name)])))
(s/def ::matches (stc/spec (s/coll-of ::find-match :kind vector?)))
(s/def ::find-result (stc/spec (s/keys :req-un [::matches])))

(s/def ::created (stc/spec {:spec (s/keys :req [:variation/id])
                            :description "A mapping describing a newly created variation."}))

(s/def ::names (stc/spec {:spec (s/coll-of (s/or :variation/name :variation/name) :min-count 1)
                          :description "A collection of variation names."}))

