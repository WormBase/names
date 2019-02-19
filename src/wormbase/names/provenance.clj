(ns wormbase.names.provenance
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [datomic.api :as d]
   [java-time :as jt]
   [wormbase.db :as wdb]
   [wormbase.util :as wu]
   [wormbase.names.agent :as wna]
   [wormbase.specs.person :as wsp]))

(def pull-expr '[* {:provenance/what [:db/ident]
                    :provenance/who [:person/name :person/email :person/id]
                    :provenance/how [:db/ident]}])

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
  (let [person-email (-> request :identity :person :email)
        prov (get payload :prov {})
        person-lur (or (person-lur-from-provenance prov)
                       [:person/email person-email])
        who (-> request :db (d/pull '[:db/id] person-lur) :db/id)
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

(defmulti resolve-change (fn [db change]
                           (get change :attr :default)))

(defmethod resolve-change :default
  [db change]
  (let [cv (:value change)]
    (d/ident db (:value change))))

(def ref-change-rules
  '[[(ref-changes ?e ?aname ?a ?eid)
     [?a :db/ident ?aname]
     [(namespace ?aname) ?ns]
     (not [(#{"provenance" "db"} ?ns)])]])

(defn- convert-change
  "Convert entity ids to their \"primary\" identifier."
  [db eid change-data]
  (let [change (cond ;; Inverts :eid and :value when we have a reverse ref
                 (and (= (:value change-data) eid)
                      (not= (:eid change-data) eid))
                 (assoc change-data
                        :eid (:value change-data)
                        :value (:eid change-data))
                 (= (:eid change-data) eid)
                 change-data)]
    (some-> change
            (update :value
                    (fn resolve-change-value [value]
                      (or (resolve-change db change) value)))
            (dissoc :eid))))

(defn query-tx-changes-for-event
  "Return the set of attribute and values changed for an entity."
  [db entity-id tx]
  (let [focus-eid (:db/id (d/pull db '[*] entity-id))]
    (->> (d/q '[:find ?e ?aname ?v ?added
                :in $h ?tx
                :where
                (not [$h ?e ?tx])
                [$h ?e ?a ?v ?tx ?added]
                [$h ?a :db/ident ?aname]]
              (d/history db)
              tx)
         (map #(zipmap [:eid :attr :value :added] %))
         (map (partial convert-change db focus-eid))
         (remove (comp nil? :value))
         (sort-by (juxt :attr :added :value)))))

(defn involved-in-txes
  "Return a sequence of tx ids that involve `entity-id`."
  [db entity-id]
  (d/q '[:find [?tx ...]
         :in $h ?e
         :where
         [$h ?e _ _ ?tx _]]
       (d/history db)
       entity-id))

(defn pull-provenance [db entity-id prov-pull-expr pull-changes-fn tx]
  (-> db
      (wdb/pull prov-pull-expr tx)
      (update :changes (fnil identity (pull-changes-fn tx)))))

(defn query-provenance
  "Query for the entire history of an entity `entity-id`.

  Passed two parameters:
    `db` - A datomic database.
    `entity-id` - Datomic lookup-ref or :db/id number.

  and optionally a custom pull expression to retrive the results (3-arity):
  `prov-pull-expr` should be a pull expression describing the attributes desired from
                   a datomic pull operation for the entity id.

  Returns a sequence of mappings describing the entity history."
  ([db entity-id]
   (query-provenance db entity-id pull-expr))
  ([db entity-id prov-pull-expr]
   (let [pull-changes (partial query-tx-changes-for-event db entity-id)
         pull-prov (partial pull-provenance db entity-id prov-pull-expr pull-changes)
         sort-mrf #(sort-events-by-when % :most-recent-first true)
         tx-ids (involved-in-txes db entity-id)
         prov-seq (map pull-prov tx-ids)]
     (some->> prov-seq
              (remove (fn [v]
                        (if-let [what (:provenance/what v)]
                          (and (string? what) (str/includes? what "import")))))
              (map #(update % :provenance/how (fnil identity :agent/importer)))
              (sort-mrf)
              (seq)))))
