(ns wormbase.names.recent
  (:require [datomic.api :as d]))

(def ^:dynamic *default-since* 60)

(defn activities
  "Return recent activities for both batch and individual operations.
  The result should be map whose keys represent these two groupings.
  The groupings in turn should be a sequence of maps."
  [db & {:keys [since]
         :or {since *default-since*}}]
  ;; TODO: define query for each group
  ;;;      probably apply a transform fn to query result to format the data for presentation.
  (let [since-t (d/t->tx since)]
    {:batch (d/q '[:find ?e ?a ?v ?tx
                   :in $ ?t
                   :where
                   [?e ?a ?v ?t]]
                 (d/history db)
                 since-t)
     :individual (d/q '[:find ?what ?when ?how ?how
                        :in $ ?t
                        :where
                        [?tx :provenance/what ?what]
                        [?tx :provenance/when ?when]
                        [?tx :provenance/how ?how]
                        [?tx :provenance/who ?who]]
                      (d/history db)
                      since-t)}))

