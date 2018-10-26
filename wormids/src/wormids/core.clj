(ns wormids.core
  (:refer-clojure :exclude [update])
  (:require
   [clojure.walk :as w]
   [datomic.api :as d]
   [clj-uuid :as uuid]
   [wormids.util :refer [cas-batch]]))

(defn latest-id
  "Get the latest identifier for a given `ident`."
  [db ident]
  (d/q '[:find (max ?gid) .
         :in $ ?ident
         :where
         [?e ?ident ?gid]]
       (d/history db)
       ident))

(defn parse-digits [identifier]
  (some->> identifier
           (re-seq #"0*(\d+)")
           (flatten)
           (last)
           (read-string)))

(defn latest-id-number
  "Get the numeric suffix of the latest identifier for a given `ident`."
  [db ident]
  (if-not (d/entid db ident)
    (throw (ex-info "Invalid ident" {:ident ident}))
    (or (parse-digits (latest-id db ident)) 0)))

(defn next-identifier
  "Get the next sequential identifier for a given ident."
  [db ident template]
  (->> (latest-id-number db ident) inc (format template)))

(defn allocate-id-block
  [db uiident coll]
  (assert (>= (count coll) 1))
  (let [{template :template/format} (d/pull db
                                            '[:template/format]
                                            [:template/describes uiident])
        start-n (inc (latest-id-number db uiident))
        stop-n (-> coll count (+ start-n))]
    (->> (range start-n stop-n)
         (map inc)
         (map (partial format template))
         (map (partial array-map uiident))
         (interleave coll)
         (partition 2)
         (map (partial apply merge)))))

(defn sagaize-prov [prov]
  (assoc prov
         :db/id "datomic.tx"
         :saga/id (uuid/v1)))

(defn conj-prov [prov tx-batch]
  (let [sp (sagaize-prov prov)]
    (map (fn add-prov [txes]
           (conj txes sp))
         tx-batch)))

(defn new
  "Create new entities, assiging a new WB identifiers.

  `db` - the names database value.
  `uiident`  - A datomic ident that uniquely identifies a wormbase entity.
  `coll` - A collection of one or more mappings from which to record corresponding new entities.


  Returns a collection of data structures, each suitable for passing to `datomic.api/transact`."  
  [conn uiident coll prov & {:keys [auto-increment?
                                  batch-size]
                           :or {auto-increment? true
                                batch-size 100}}]
  (let [db (d/db conn)]
    (doseq [tx-batches (->> coll
                            (partition-all batch-size)
                            (map (partial allocate-id-block db uiident)))]
      (doseq [tx-batch tx-batches]
        (prn tx-batch)
        @(d/transact conn (conj-prov prov tx-batch))))))

(defn update
  "Update entities identified by `uiident` with data provided in `coll`.

  `db` - the names database value.
  `uiident` - The datomic `ident` that uniquely identifies an entity for each mapping in `coll`.
  `coll` - the collection of mappings to transact.

  Each mapping in `coll` should contain a entry containing the key
  `uiident`, uniquely identifiying the entity to update.

  Returns a collection of data structures, each suitable for passing to `datomic.api/transact`."
  [db uiident coll prov & {:keys [batch-size]
                           :or {batch-size 100}}]
  (->> coll
       (map (fn gen-cas-batch [m]
              (first (cas-batch db (find m uiident) m))))
       (remove nil?)
       (partition-all batch-size)
       (map (partial conj-prov prov))))

(defn change-status
  "Change the status of one more entities.

  Update the status of type `status-ident` to `new-status` for each
  entity in `coll` identified by datomic ident `uiident`.

  `db` - the names database value.
  `uiident` - A datomic ident that identifies an entity for each mappping in `coll`
  `coll` - the collection of mappings, providing the status-attribute and value to be set.

  Returns a collection of data structures, each suitable for passing to `datomic.api/transact`."
  [db uuident coll])
