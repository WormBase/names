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
  "Get the next identifier for a given `ident`."
  [db ident template]
  (->> (latest-id-number db ident) inc (format template)))

(defn allocate-block
  "Allocate a block of sequential monitonically increasing identifiers.

  `template` - A c-style template string that can be used to format identifiers for a `uiident`.
  `uiident` - A datomic ident that uniquely identifies an entity.
  `coll` - A collection of mappings that contain data to be transacted.
           Maps in this collection will be augmented with identifiers associated with `uiident`.

  Returns data suitable for passing to `datomic.api/transact`."
  [db template uiident coll]
  (assert (>= (count coll) 1))
  (let [lid (latest-id-number db uiident)
        start-n (inc lid)
        stop-n (-> coll count (+ start-n))]
    (->> (range start-n stop-n)
         (map inc)
         (map (partial format template))
         (map (partial array-map uiident))
         (interleave coll)
         (partition 2)
         (map (partial apply merge)))))

