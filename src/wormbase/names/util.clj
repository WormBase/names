(ns wormbase.names.util
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [clojure.walk :as w]
   [aero.core :as aero]
   [datomic.api :as d]))

(defn read-app-config
  ([]
   (read-app-config "config.edn"))
  ([resource-filename]
   (aero/read-config (io/resource resource-filename))))

(defn- nsify [domain kw]
  (if (namespace kw)
    kw
    (keyword domain (name kw))))

(defn namespace-keywords
  "Add namespace `domain` to keys in `data` mapping.

  Used to setup data to be consistent for specs without requiring
  input data (that typically comes from JSON) to use fully-qualified
  namespaces.

  Returns a new map."
  [domain data]
  (map #(reduce-kv (fn [rec kw v]
                     (-> rec
                         (dissoc kw)
                         (assoc (nsify domain kw) v)))
                   (empty %)
                   %)
       data))

;; trunc and datom-table taken from day-of-datomic repo (congnitect).

(defn trunc
  "Return a string rep of x, shortened to n chars or less"
  [x n]
  (let [s (str x)]
    (if (<= (count s) n)
      s
      (str (subs s 0 (- n 3)) "..."))))

(defn datom-table
  "Print a collection of datoms in an org-mode compatible table."
  [db datoms]
  (->> datoms
       (map
        (fn [{:keys [e a v tx added]}]
          {"part" (d/part e)
           "e" (format "0x%016x" e)
           "a" (d/ident db a)
           "v" (if (nat-int? v)
                 (or (d/ident db v)
                     (format "0x%016x" (:db/id (d/entity db v))))
                 (trunc v 24))
           "tx" (format "0x%x" tx)
           "added" added}))
       (pp/print-table ["part" "e" "a" "v" "tx" "added"])))


(defn select-keys-with-ns [data key-ns]
  (into {} (filter #(= (namespace (key %)) key-ns) data)))

(defn- resolve-ref [db m k v]
  (cond
    (and v (pos-int? v))
    (if-let [ident (d/ident db v)]
      (assoc m k ident)
      (assoc m k (d/pull db '[*] v)))
    :default
    (assoc m k v)))

(defn resolve-refs [db entity-like-mapping]
  (w/prewalk (fn [xs]
               (if (map? xs)
                 (reduce-kv (partial resolve-ref db) (empty xs) xs)
                 xs))
             entity-like-mapping))

(defn has-status?
  "Return truthy if entity `ent` has status `status`.

  `status` can be datomic entity or keyword/ident."
  [status ent]
  (some (partial = status)
        ((juxt identity :db/ident) ent)))

(def live? (partial has-status? :gene.status/live))

(def dead? (partial has-status? :gene.status/dead))

(def suppressed? (partial has-status? :gene.status/suppressed))

(def not-live? (comp not live?))

