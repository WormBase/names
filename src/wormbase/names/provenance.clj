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
      (first (vec who)))))

(defn assoc-provenance
  "Associate provenance data with the request.

  Fill in defaults where not specified by the request.
  The `how` (agent) is always assigned (not overridable by data in request).

  Returns a map."
  [request payload what]
  (let [id-token (-> request :identity :token)
        prov (or (wnu/select-keys-with-ns payload "provenance") {})
        person-lur (or (person-lur-from-provenance prov)
                       [:person/email (.getEmail id-token)])
        who (-> request :db (d/entity person-lur) :db/id)
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

(defn query-tx-changes-for-event
  "Return the set of attribute and values changed for a given gene transaction."
  [db entity-id tx]
  (->> (d/q '[:find ?aname ?v ?added
              :in $ ?e ?tx
              :where
               [?e ?a ?v ?tx ?added]
               [?a :db/ident ?aname]]
              (d/history db)
            entity-id
            tx)
       (remove (fn remove-importer-artifact [[k _ _]]
                 (= k :importer/historical-gene-version)))
       (map (fn convert-entids [result-map]
              (reduce-kv (fn [m k v]
                           (update m k (fnil (partial d/ident db) v)))
                         (empty result-map)
                         result-map)))
       (map #(zipmap [:attr :value :added] %))
       (sort-by :attr)))

(defn pull-provenance [db entity-id prov-pull-expr pull-changes-fn tx]
  (-> db
      (d/pull prov-pull-expr tx)
      (update :changes (fnil identity (pull-changes-fn tx)))))

(defn query-provenance
  "Query for the entire history of an entity `entity-id`.
  `entity-id` can be a datomic id number or a lookup-ref."
  [db entity-id prov-pull-expr default-event]
  (let [pull-changes (partial query-tx-changes-for-event db entity-id)
        pull-prov (partial pull-provenance db entity-id prov-pull-expr pull-changes)
        sort-mrf #(sort-events-by-when % :most-recent-first true)
        tx-ids (d/q '[:find [?tx ...]
                      :in $ ?e
                      :where
                      [?e _ _ ?tx _]]
                    (d/history db)
                    entity-id)]
    (some->> tx-ids
             (map pull-prov)
             (map wnu/undatomicize)
             (remove #(= (:provenance/what %) :event/import-gene))
             (map #(update % :provenance/how (fnil identity :agent/importer)))
             (sort-mrf)
             seq)))
