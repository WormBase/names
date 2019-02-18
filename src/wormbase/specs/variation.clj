(ns wormbase.specs.variation
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.spec :as sts]
   [spec-tools.core :as stc]))

(def id-regexp #"WBVar\d{8}")

(def name-regexp #"(([a-z]+)(Df|Dp|Ti|T|Is)?(\d+)){1,4}")

(s/def :variation/id (stc/spec {:spec (s/and string? (partial re-matches id-regexp))}))

(s/def :variation/name (stc/spec {:spec (s/and string?
                                               (partial re-matches name-regexp))}))

(s/def :variation/status sts/keyword?)

(s/def ::identifier (stc/spec (s/or :variation/id :variation/id
                                    :variation/name :variation/name)))

(s/def :variation/status sts/keyword?)

(s/def ::info (stc/spec (s/keys :req [:variation/id :variation/status]
                                :opt [:variation/name])))

(s/def ::status-changed (stc/spec (s/keys :req [:variation/status])))

(s/def ::new (stc/spec (s/keys :req [:variation/name])))

(s/def ::update ::new)

(s/def ::new-batch (stc/spec (s/coll-of ::new :min-count 1)))

(s/def ::update-batch (stc/spec
                       (s/coll-of
                        (s/merge ::update (s/keys :req [:variation/id]))
                        :min-count 1)))

(s/def ::change-status-batch (stc/spec (s/coll-of ::identifier :min-count 1)))
(s/def ::kill-batch ::change-status-batch)
(s/def ::resurrect-batch ::change-status-batch)

(s/def ::find-match (stc/spec (s/keys :req [(or :variation/id :variation/name)])))
(s/def ::matches (stc/spec (s/coll-of ::find-match :kind vector?)))
(s/def ::find-result (stc/spec (s/keys :req-un [::matches])))

(s/def ::created (stc/spec (s/keys :req [:variation/id])))

(s/def ::names (stc/spec (s/coll-of (s/or :variation/name :variation/name) :min-count 1)))

