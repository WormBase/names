(require '[datomic.api :as d])

;;Change uri as appropriate on deploying to different environments
(def uri "datomic:ddb-local://localhost:8000/WBNames_local/wormbase")
(def conn (d/connect uri))
(def db (d/db conn))

;; Drop all currently stored auth-tokens (invalidated by new code, so no longer usable)
(d/transact conn (map (fn [entity] [:db/retract (first entity) :person/auth-token (second entity)]) (d/q '[:find ?e ?authtoken :where [?e :person/email ?email] [?e :person/name ?name] [?e :person/auth-token ?authtoken]] db)))

;; Install new attributes for auth-token and account usage tracking
(def new-attributes [#:db{:ident :person/auth-token-stored-at,
                          :valueType :db.type/instant,
                          :cardinality :db.cardinality/one,
                          :doc "When the current auth-token was stored."}
                     #:db{:ident :person/auth-token-last-used,
                          :valueType :db.type/instant,
                          :cardinality :db.cardinality/one,
                          :doc "When the stored auth-token was last used to access the API."}
                     #:db{:ident :person/last-activity,
                          :valueType :db.type/instant,
                          :cardinality :db.cardinality/one,
                          :doc "When the user last showed any activity in the NS (through either API or web)."}])

(d/transact conn new-attributes)
