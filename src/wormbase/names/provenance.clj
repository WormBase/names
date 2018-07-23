(ns wormbase.names.provenance
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.walk :as w]
   [datomic.api :as d]
   [java-time :as jt]
   [wormbase.db :as wdb]
   [wormbase.names.agent :as wna]
   [wormbase.specs.person :as wsp]
   [wormbase.names.util :as wnu]))

(defn- person-lur-from-provenance
  "Return a datomic `lookup ref` from the provenance data."
  [prov]
  (when-let [who (:provenance/who prov)]
    (if (string? who)
      (when (s/valid? ::wsp/identifier who)
        (s/conform ::wsp/identifier who))
      (first (into [] who)))))

(defn assoc-provenence
  "Associate provenance data with the request.

  Fill in defaults where not specified by the request.
  The `how` (agent) is always assigned (not overridable by data in request).

  Returns a map."
  [request payload what]
  (let [id-token (:identity request)
        prov (or (wnu/select-keys-with-ns payload "provenance") {})
        ;; TODO: accept :person/email OR :person/id (s/conform...)
        person-lur (or (person-lur-from-provenance prov)
                       [:person/email (:email id-token)])
        who (:db/id (d/entity (:db request) person-lur))
        whence (get prov :provenance/when (jt/to-java-date (jt/instant)))
        how (wna/identify id-token)
        why (:provenance/why prov)
        prov {:db/id "datomic.tx"
              :provenance/what what
              :provenance/who who
              :provenance/when whence
              :provenance/how how}]
    (merge
     prov
     (when (nil? how)
       {:provenance/how :agent/importer})
     (when-not (str/blank? why)
       {:provenance/why why})
     (when (inst? whence)
       {:provenance/when whence}))))

(defn sort-events-by-when
  "Sort a sequence of mappings representing events in temporal order."
  [events & {:keys [most-recent-first]
             :or {most-recent-first false}}]
  (let [cmp (if most-recent-first
              #(compare %2 %1)
              #(compare %1 %2))]
    (sort-by :provenance/when cmp events)))

(defn pull-provenance
  "Retrive data from `db` via pull exporession."
  [db tx-id]
  (d/pull db
          '[* {:provenance/what [:db/ident]
               :provenance/who [:person/id :person/name]
               :provenance/how [:db/ident]
               :provenance/split-from [:gene/id]
               :provenance/split-into [:gene/id]
               :provenance/merged-from [:gene/id]
               :provenance/merged-into [:gene/id]}]
          tx-id))

(defn- undatomicize
  "Remove datomic internal attribute/value pairs from a seq of maps."
  [data]
  (w/postwalk (fn presenter [m]
                (cond
                  (and (map? m) (:db/ident m))
                  (:db/ident m)
                  (map? m) (dissoc m :db/id :db/txInstant)
                  :default m))
              data))

(defn query-provenance
  "query for the entire history of an entity `entity-id`.
  Note that lookup-ref can be used instead of a numeric identifier."
  [db entity-id]
  (let [pull (partial pull-provenance db)
        sort-mrf #(sort-events-by-when % :most-recent-first true)]
    (->> (d/q '[:find [?tx ...]
                :in $ ?e
                :where
                [?e _ _ ?tx _]]
              (d/history db)
              entity-id)
         (map pull)
         (map undatomicize)
         (remove #(= (:provenance/what %) :event/import-gene))
         (sort-mrf)
         seq)))
