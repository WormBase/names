(ns wormbase.specs.recent
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [wormbase.specs.provenance :refer :all]))

(defn provenance? [k]
  (#{"provenance" "db"} (namespace k)))

(s/def ::activity (stc/spec (s/map-of provenance? any?)))

(s/def ::activities (stc/spec (s/coll-of ::activity)))


