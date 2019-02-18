(ns wormbase.names.recent
  (:require
   [datomic.api :as d]
   [java-time :as jt]
   [wormbase.db :as wdb]
   [wormbase.names.provenance :as wnp]))

(def ^:dynamic *days-ago* 60)

(defn activities
  "Return recent activities for both batch and individual operations.
  The result should be map whose keys represent these two groupings.
  The groupings in turn should be a sequence of maps."
  [db log rules & {:keys [needle n-days]
                   :or {needle ""
                        n-days *days-ago*}}]
  ;; TODO: define query for each group
  ;;;      probably apply a transform fn to query result to format the data for presentation.
  (let [now (jt/to-java-date (jt/instant))
        since-t (-> (jt/instant)
                    (jt/minus (jt/days n-days))
                    (jt/to-java-date))
        since-db (d/since db since-t)
        hdb (d/history since-db)]
    (some->> (d/q '[:find [?tx ...]
                    :in $ $hdb ?log ?now ?offset ?needle %
                    :where
                    [(tx-ids ?log ?now ?offset) [?tx ...]]
                    [$hdb ?tx ?a ?v]
                    [$ ?a :db/ident ?aname]
                    ;; [(missing? $hdb ?tx :batch/id)]
                    ;; [$hdb ?tx :provenance/what ?what]
                    ;; [$ ?what :db/ident ?what-kw]
                    ;; [(name ?what-kw) ?what-name]
                    ;; [(clojure.string/includes? ?what-name ?needle)]
                    ($ filter-events ?tx ?aname ?v ?needle)
                    ]
                  db
                  hdb
                  log
                  since-t
                  now
                  needle
                  rules)
             (map (partial wdb/pull since-db wnp/pull-expr)))))

(def entity-rules '[[(filter-events ?tx ?aname ?v ?needle)
                     [(missing? $ ?tx :batch/id)]
                     [?tx :provenance/what ?what]
                     [?what :db/ident ?what-kw]
                     [(name ?what-kw) ?what-name]
                     [(clojure.string/includes? ?what-name ?needle)]]])

(def person-rules '[[(filter-events ?tx ?aname ?v ?needle)
                     [?tx :provenance/who ?pid]
                     [?pid :person/email ?match-string]]])

(def batch-rules '[[(filter-events ?tx ?aname ?v ?needle)
                    [?tx :batch/id _ _ ]]])



(comment
  "Examples of each invokation flavour"
  (activities db (d/log conn) entity-rules :needle "gene" :n-days 40)

  (activities db (d/log conn) person-rules :needle "matthew.russell@wormbase.org" :n-days 40)
  (activities db (d/log conn) batch-rules :needle "" :n-days 40))
