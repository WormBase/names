(ns wormbase.specs.recent
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [wormbase.specs.provenance :as wsp]))

(s/def ::from (s/nilable inst?))

(s/def ::until (s/nilable inst?))

(s/def ::activities (stc/spec ::wsp/history))
