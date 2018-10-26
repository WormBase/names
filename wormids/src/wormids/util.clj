(ns wormids.util
  (:require
   [clojure.walk :as w]
   [datomic.api :as d]))

(defn resolve-refs
  "Resolve datomic db references in entity-map `em`."
  [db em]
  (w/postwalk
      (fn [xs]
        (if (map? xs)
          (reduce-kv (fn [m k v]
                       (cond
                         (and (map? v) (:db/id v))  (assoc m k (:db/id v))
                         (keyword? v) (assoc m k [:db/ident v])
                         (vector? v) (assoc m k (:db/id (d/entity db v)))
                         :else (assoc m k v)))
                     {}
                     xs)
          xs))
      em))

(defn cas-batch
  "Collate a collection of compare-and-swap operations."
  [db eid data]
  (let [entity-map (d/pull db '[*] eid)
;;        _ (prn entity-map)
        new (resolve-refs db data)
        res (some->> new
                     (map (fn [[k v]]
                            (let [old-v (k (resolve-refs db entity-map))]
                              (when-not (or (nil? v)
                                            (= old-v v))
                                [:db.fn/cas eid k old-v v]))))
                     (remove nil?))]
    res))

