(ns wormbase.specs.stats
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]))

(s/def ::summary (stc/spec {:spec (s/map-of #{:gene/id
                                              :variation/id
                                              :sequence-feature/id
                                              :batch/id}
                                            int?)
                            :swagger/example {:gene/id 1
                                              :variation/id 2
                                              :sequence-feature/id 3
                                              :batch/id 4}
                            :description "A map of identifiers to the count of entities stored in the system."}))

