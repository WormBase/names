(ns wormbase.specs.biotype
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]))

(s/def ::identifier (stc/spec {:spec (s/and string? not-empty)}))

  
