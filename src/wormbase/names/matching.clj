(ns wormbase.names.matching)

(def rules
  '[[(matches-name ?attr ?pattern ?name ?eid)
     [(re-seq ?pattern ?name)]
     [?eid ?a ?name]
     [?a :db/ident ?attr]]])

(defn make-rules
  "Make matching rules finding an entity by certain attributes.
  Returns a quoted form."
  [rule-name idents]
  (->> idents
       (map (fn [ident]
              (conj
               [(cons (symbol rule-name) '(?pattern ?name ?eid))]
               (list 'matches-name ident '?pattern '?name '?eid))))
       (concat rules)
       (vec)))
