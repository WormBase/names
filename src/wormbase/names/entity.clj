(ns wormbase.names.entity
  (:require
   [clojure.spec.alpha :as s]
   [datomic.api :as d]
   [wormbase.db :as wdb]))

(defn identify
  "Return an lookup ref and entity for a given identifier.
  Lookups `identifier` (conformed with `identify-spec`) in the database.
  Returns `nil` when the entity cannot be found."
  [identitfy-spec request identifier]
  ;;;;; TODO: this isn't good enough anymore.
  ;;;;        the specs for gene/cgc-name and gene/sequence-name used to be complex enough
  ;;;;        to determin which attribute to use given the provideed`identifier`,
  ;;;;        - now specs have been simpified (moved regexp to db), we need to query
  ;;;;        - the db for a species specific pattern.... (url needs to change to include species?) -
  ;;;;        - or more likely, need to match against every species regexp patttern combo...
  ;;;;        YIKES.
  ;;;;        without this, the /gene/<foo> won't auto-resolve anymore for sequence-names,
  ;;;;        as the or-specs default to CGC (being listed first in most or-spec defs)
  (when-not (s/valid? identitfy-spec identifier)
    (throw (ex-info "Found one or more invalid identifiers."
                    {:problems (s/explain-data identitfy-spec identifier)
                     :type ::validation-error})))
  (let [lookup-ref (s/conform identitfy-spec identifier)
        db (:db request)
        ent (d/entity db lookup-ref)]
    [lookup-ref ent]))
