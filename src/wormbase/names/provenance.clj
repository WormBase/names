(ns wormbase.names.provenance
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [datomic.api :as d]
            [java-time.api :as jt]
            [wormbase.db :as wdb]
            [wormbase.names.agent :as wna]
            [wormbase.names.util :as wnu]
            [wormbase.specs.person :as wsp]
            [wormbase.util :as wu]))

(def pull-expr '[:provenance/when
                 :provenance/why
                 :batch/id
                 [:db/txInstant :as :t]
                 {:provenance/what [:db/ident]
                  :provenance/who [:person/name :person/email :person/id]
                  :provenance/how [:db/ident]}])

(defn person-lur-from-provenance
  "Return a datomic `lookup ref` from the provenance data."
  [prov]
  (when-let [who (:provenance/who prov)]
    (when (s/valid? ::wsp/identifier who)
      (s/conform ::wsp/identifier who)
      (throw (ex-info "Invalid provenance identifier."
                      {:type :user/validation-errror})))))

(defn assoc-provenance
  "Associate provenance data with the request.

  Fill in defaults where not specified by the request.
  The `how` (agent) is always assigned (not overridable by data in request).

  Returns a map."
  [request payload what & {:keys [tz]
                           :or {tz (jt/zone-id "UTC")}}]
  (let [auth-identity (:identity request)
        prov (wnu/qualify-keys (get payload :prov {}) "provenance")
        who (if-let [plur (person-lur-from-provenance (update prov
                                                              :provenance/who
                                                              (fn [x]
                                                                (wnu/qualify-keys x "person"))))]
              (-> request :db (d/pull [:db/id] plur))
              (-> auth-identity :person :db/id))
        whence (-> (get prov :provenance/when (jt/instant))
                   (jt/zoned-date-time tz)
                   (jt/with-zone-same-instant tz)
                   (jt/java-date))
        how (-> request :headers wna/identify)
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
     (when-not (or (nil? why) (str/blank? why))
       {:provenance/why why})
     (when (inst? whence)
       {:provenance/when whence}))))

(defmulti resolve-change (fn [_ change]
                           (get change :attr :default)))

(defmethod resolve-change :provenance/who
  [db change]
  (assoc change
         :value
         (d/pull db [:person/id
                     :person/email
                     :person/name] (:value change))))

(defmethod resolve-change :default
  [db change]
  (if (and ((-> change :attr namespace set) "counter")
           (= (-> change :attr name) "value"))
    (select-keys change [:eid])
    (assoc change :value (d/ident db (:value change)))))

(def ref-change-rules
  '[[(ref-changes ?e ?aname ?a ?eid)
     [?a :db/ident ?aname]
     [(namespace ?aname) ?ns]
     (not [(#{"provenance" "db"} ?ns)])]])

(defn- convert-reverse-ref [_ eid change-data]
  (if (= (:eid change-data) eid)
    change-data
    (assoc change-data
           :value (:eid change-data)
           :eid (:value change-data))))

(defn- convert-change
  "Convert entity ids to provenance representation."
  [db eid change-data]
  (let [cd (convert-reverse-ref db eid change-data)]
    (when-let [cd (or (resolve-change db cd) cd)]
      (dissoc cd :eid))))

(defn entire-history [db entity-id]
  (d/q '[:find ?e ?aname ?v ?tx ?added
         :in $h ?e
         :where
         [$h ?e ?a ?v ?tx ?added]
         [$h ?a :db/ident ?aname]]
       (d/history db)
       entity-id))

(def ^:private exclude-nses (set/union (wu/datomic-internal-namespaces)
                                       #{"importer" "counter"}))

(defn tx-changes [db log tx]
  (->> tx
       (d/q '[:find ?e ?a ?v ?added
              :in $ ?log ?tx
              :where
              [(tx-data ?log ?tx) [[?e ?a ?v _ ?added]]]]
            db
            log)
       (map (partial zipmap [:eid :attr :value :added]))
       (map #(update % :attr (partial d/ident db)))
       (remove #(exclude-nses (-> % :attr namespace)))))

(defn query-tx-changes-for-event
  "Return the set of attribute and values changed for an entity."
  [db log entity-id tx]
  ;; using d/entid to normalise the form of entity-id (could be lookup ref) to datomic numeric eid.
  (let [eid (d/entid db entity-id)]
    (->> (tx-changes db log tx)
         (remove (fn [change]
                   ((-> change :attr namespace set) "counter")))
         (filter (fn [change]
                   (some #(= % eid) ((juxt :eid :value) change))))
         (map (partial convert-change db eid))
         (map #(update % :attr wnu/unqualify-maybe))
         (map #(update % :value wnu/unqualify-maybe))
         (sort-by (juxt :attr :added :value)))))

(defn involved-in-txes
  "Return a sequence of tx ids that involve `entity-id`."
  [db entity-id ref-idents]
  (let [ent-txes (d/q '[:find [?tx ...]
                        :in $h ?e
                        :where
                        [$h ?e _ _ ?tx _]]
                      (d/history db)
                      entity-id)
        ref-txes (mapcat #(d/q '[:find [?tx ...]
                                 :in $h ?e ?ref-attr
                                 :where
                                 [$h _ ?ref-attr ?e ?tx _]]
                               (d/history db)
                               entity-id
                               %)
                         ref-idents)]
    (-> (concat ent-txes ref-txes) set sort)))

(defn pull-provenance
  ([db _ prov-pull-expr tx]
   (-> (wdb/pull db prov-pull-expr tx)
       (update :provenance/how wnu/unqualify-maybe)
       (update :provenance/what wnu/unqualify-maybe)
       (update :provenance/when
               (fn [v]
                 (when v
                   (jt/zoned-date-time v (jt/zone-id)))))
       (update :provenance/who
               ;; Temporary hack to fix non-assignment of person provenance in importer.
               (fnil identity
                     {:person/email "matthew.russell@wormbase.org"}))))
  ([db entity-id prov-pull-expr tx pull-changes-fn]
   (let [changes (pull-changes-fn tx)]
     (-> (pull-provenance db entity-id prov-pull-expr tx)
         (update :changes (fnil identity changes))))))

(defn query-provenance
  "Query for the entire history of an entity `entity-id`.

  Passed two parameters:
    `db` - A datomic database.
    `entity-id` - Datomic lookup-ref or :db/id number.

  and optionally a custom pull expression to retrive the results (3-arity):
  `prov-pull-expr` should be a pull expression describing the attributes desired from
                   a datomic pull operation for the entity id.

  Returns a sequence of mappings describing the entity history."
  ([db log entity-id]
   (query-provenance db log entity-id #{}))
  ([db log entity-id ref-attrs]
   (query-provenance db log entity-id ref-attrs pull-expr))
  ([db log entity-id ref-attrs prov-pull-expr]
   (let [pull-changes (partial query-tx-changes-for-event db log entity-id)
         pull-prov #(pull-provenance db entity-id prov-pull-expr % pull-changes)
         sort-mrf #(wu/sort-events-by :provenance/when % :most-recent-first true)
         tx-ids (involved-in-txes db entity-id ref-attrs)
         prov-seq (seq (map pull-prov tx-ids))]
     (some->> prov-seq
              (sort-mrf)
              (seq)))))
