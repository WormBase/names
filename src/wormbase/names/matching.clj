(ns wormbase.names.matching)

(def name-matching-rules
  '[[(matches-name ?attr ?pattern ?name ?eid)
     [(re-seq ?pattern ?name)]
     [?a :db/ident ?attr]
     [?eid ?a ?name]]])

