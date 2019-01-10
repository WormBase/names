(ns wormbase.ids.batch
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
   [clojure.core :as cc]
   [datomic.api :as d]
   [wormbase.ids.core :refer [attr-schema-unique? identifier-format]]))

(defn- assoc-prov
  "Attach an identifier to `prov` making this a mapping suitable for tracking provenance for a batch.

  A `UUID` under the key `:batch/id` is added."
  [prov]
  (assoc prov :db/id "datomic.tx" :batch/id (d/squuid)))

(defn- add-prov-maybe [prov tx-data]
  (when tx-data
    (cons prov tx-data)))

(defn db-error? [exc]
  (some->> (ex-data exc)
           (keys)
           (filter (fn has-db-ns? [k]
                     (#{"db" "db.error"} (namespace k))))))

(defrecord BatchResult [tx-result errors])

(defn attempt-batch
  [result xs]
  (let [result* (try
                  (cc/update result
                             :tx-result
                             (fn [tx-res]
                               (d/with (:db-after tx-res) xs)))
                  (catch Exception exc
                    (if (some-> exc ex-data :db/error)
                      (cc/update result
                                 :errors
                                 (fnil (fn [curr-val]
                                         (conj curr-val exc)) []))
                      (throw exc))))]
    (assert result* "tx-res was nil!")
    result*))

(defn process-batch
  [processor-fn conn uiident coll prov batch-size]
  (let [sp (assoc-prov prov)
        batch (partition-all batch-size coll)
        db (d/db conn)
        init-tx-res (map->BatchResult {:tx-result {:db-after db} :errors nil})]
    (let [result (reduce
                  (partial processor-fn
                           sp
                           (fn [result xs]
                             (when (get-in result [:tx-result :db-after])
                               (attempt-batch result xs))))
                  init-tx-res
                  batch)]
      (when-let [errors (-> result :errors seq)]
        (throw (ex-info "Errors during attempting batch"
                        {:errors errors
                         :type ::db-errors})))
      (let [b-result (reduce (partial processor-fn
                                      sp
                                      (fn [_ xs]
                                        (assoc result
                                               :tx-result
                                               @(d/transact-async conn xs))))
                             (map->BatchResult {:tx-result {:db-after db}})
                             batch)]
        (when (get-in b-result [:tx-result :db-after])
          (apply array-map (find sp :batch/id)))))))

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
         (some->> [['wormbase.ids.core/new template uiident xs] sp]
                  (transact-fn db))))
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
                       ['wormbase.ids.core/cas-batch (find item uiident) item]))
                (add-prov-maybe sp)
                (transact-fn db))))
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
                (transact-fn db))))
   conn
   uiident
   coll
   prov
   batch-size))