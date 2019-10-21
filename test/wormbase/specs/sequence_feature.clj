(ns wormbase.specs.sequence-feature
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]))

(def id-regexp #"WBsf\d+")

(def max-new-limit 100000)

(s/def ::n (s/and pos-int? #(< % max-new-limit)))

(s/def :sequence-feature/id (s/and string? (partial re-matches id-regexp)))

(s/def ::new-batch (s/keys :req-un [::n]))

(s/def ::identifier (stc/spec {:spec (s/or :sequence-feature/id :sequence-feature/id)
                               :swagger/example "WBsf000006"
                               :description "Unique primary identifier."}))

(s/def ::change-status-batch (stc/spec (s/coll-of ::identifier :min-count 1)))

(s/def ::kill-batch ::change-status-batch)

(s/def ::resurrect-batch ::change-status-batch)


