(ns wormbase.specs.stats
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]))

(s/def ::summary (stc/spec
                  {:spec  (s/map-of string? int?)
                   :swagger/example {"gene" 2000
                                     "variation" 2
                                     "sequence-feature" 3
                                     "batch" 400000}
                   :description "A map of identifiers to the count of entities stored in the system."}))

