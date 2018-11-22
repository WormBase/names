(ns wormids.batch
  "Batch creation, update and status changing.

  General principal here is to use `datomic.api/with` to test batches
  of transactions before passing to `datomic.api/transact`.

  Concept `saga`::
    A series of tranasctions that are grouped by a common value.
    Each operation will generate a `UUID` and attach it to the provenance associated
    with each transaction batch.

  Conflicts caused by transaction data are not handled her;
  they are expected to be handled by the caller.

  No-op transactions are avoided by examining the results of attempted transactions
  via `with` and skipping `transact` calls when there is no data to transact."
  (:refer-clojure :exclude [update])
  (:require
   [datomic.api :as d]
   [wormids.core :refer [attr-schema-unique? identifier-format]]))

(defn batch? [coll]
  (> (count coll) 1))

(defn data-transacted?
  "Determine if there was any data transacted as a result of transaction.

  Returns `nil` if only the transaction itself was asserted."
  [tx-result]
  (some->> tx-result
           :tx-data
           (map (juxt :e :tx))
           (filter (partial apply not=))
           (seq)))

(defn tx-res-when-transacted [tx-result]
  (when (data-transacted? tx-result)
    tx-result))

(defn assoc-prov
  "Attach an identifier to `prov` making this a mapping suitable for tracking provenance for a batch.

  A `UUID` under the key `:batch/id` is added."
  [coll prov]
  (cond-> prov
    true (assoc :db/id "datomic.tx")
    (batch? coll) (assoc :batch/id (d/squuid))))

(defn add-prov-maybe [prov tx-data]
  (when tx-data
    (cons prov tx-data)))

(defn process-batch
  [processor-fn conn uiident coll prov batch-size]
  (let [sp (assoc-prov coll prov)
        batch (partition-all batch-size coll)
        db (d/db conn)]
    (when-let [dba (reduce (partial processor-fn
                                    sp
                                    (fn [tx-res xs]
                                      (d/with (:db-after tx-res) xs)))
                           {:db-after db}
                           batch)]
      (let [dba* (reduce (partial processor-fn
                                  sp
                                  (fn [_ xs]
                                    @(d/transact-async conn xs)))
                         {:db-after db}
                         batch)]
        (when dba*
          (if (batch? coll)
            (apply array-map (find sp :batch/id))
            (let [db (:db-after dba*)
                  identify (comp (fn [[e a v]]
                                   (if-let [pvalue (uiident (d/pull db [uiident] e))]
                                     {uiident pvalue}
                                     {(d/ident db a) v}))
                                 (juxt :e :a :v))
                  asserted (into {} (map identify (:tx-data dba*)))]
              (reduce-kv (fn [m a v]
                           (cond-> m (attr-schema-unique? db a) (assoc a v)))
                         {}
                         asserted))))))))

(defn new
  "Create new entities, assiging a new WB identifiers.

  `conn` - The datomic connnection.
  `uiident`  - A datomic ident that uniquely identifies a wormbase entity.
  `coll` - A collection of one or more mappings from which to record corresponding new entities.
  `prov` - the provenance to assoicate with each batch.

  Returns a collection of data structures, each suitable for passing to `datomic.api/transact`."
  [conn uiident coll prov & {:keys [batch-size]
                             :or {batch-size 100}}]
  (let [template (identifier-format (d/db conn) uiident)]
    (process-batch
     (fn reduce-new [sp transact-fn db xs]
       (when db
         (some->> [['wormids.core/new template uiident xs] sp]
                  (transact-fn db)
                  tx-res-when-transacted)))
     conn
     uiident
     coll
     prov
     batch-size)))

(defn update
  "Update entities identified by `uiident` with data provided in `coll`.

  `conn` - The datomic connection.
  `uiident` - The datomic `ident`
              that uniquely identifies an entity for each mapping in `coll`.
  `coll` - A sequence of maps, each should contain a entry containing
           the key `uiident`, uniquely identifiying the entity to update.
  `prov` - the provenance to assoicate with each batch.

  A unique identifier for the batch `:batch/id` will be attached to
  the provenance for each batch to transact, and returned upon
  successful completion. If no transactions are to be done (eg. no
  changes detected), returns `nil`."
  [conn uiident coll prov & {:keys [batch-size]
                             :or {batch-size 100}}]
  (process-batch
   (fn [sp transact-fn db items]
     (when db
       (some->> (seq items)
                (map (fn [item]
                       ['wormids.core/cas-batch (find item uiident) item]))
                (add-prov-maybe sp)
                (transact-fn db)
                (tx-res-when-transacted))))
   conn
   uiident
   coll
   prov
   batch-size))

(defn remove-names
  [conn uiident attr coll prov & {:keys [batch-size]
                                        :or {batch-size 100}}]
  (process-batch
   (fn [sp transact-fn db items]
     (when (and db (seq items))
       (some->> items
                (map (fn [item]
                       (let [eid (find item uiident)
                             value (attr item)]
                         [:db/retract eid attr value])))
                (add-prov-maybe sp)
                (transact-fn db)
                (tx-res-when-transacted))))
   conn
   uiident
   coll
   prov
   batch-size))
