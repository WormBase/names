(ns wormbase.names.entity
  (:require
   [clojure.spec.alpha :as s]
   [datomic.api :as d]
   [wormbase.db :as owdb]))

(defn identify
  "Return an lookup ref and entity for a given identifier.
  Lookups `identifier` (conformed with `identify-spec`) in the database.
  Returns nil when identifier cannot be found."
  [identitfy-spec request identifier]
  (when-not (s/valid? identitfy-spec identifier)
    (throw (ex-info "Found one or more invalid identifiers."
                    {:problems (s/explain-data identitfy-spec identifier)
                     :type ::validation-error})))
  (let [lookup-ref (s/conform identitfy-spec identifier)
        db (:db request)
        ent (d/entity db lookup-ref)]
    [lookup-ref ent]))
