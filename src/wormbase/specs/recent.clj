(ns wormbase.specs.recent
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [wormbase.specs.provenance :refer :all]))

(s/def ::from (s/nilable inst?))

(s/def ::until (s/nilable inst?))

(defn provenance? [k]
  (or (#{"provenance" "t"} (namespace k))
      (#{:t} k)))

(s/def ::activity (stc/spec (s/map-of provenance? any?)))

(s/def ::activities (stc/spec (s/coll-of ::activity)))
