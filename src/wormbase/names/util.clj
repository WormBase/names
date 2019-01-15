(ns wormbase.names.util
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.walk :as w]
   [aero.core :as aero]
   [datomic.api :as d]
   [expound.alpha :refer [expound-str]]
   [ring.util.http-response :refer [bad-request
                                    conflict
                                    ok
                                    precondition-failed]]
   [spec-tools.core :as stc]
   [wormbase.db :as wdb]
   [wormbase.specs.common :as wsc]))

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
  [db datoms & {:keys [id-format tx-id-format omit-cols]
                :or {omit-cols #{}
                     id-format "0x%016x"
                     tx-id-format "0x%x"}}]
  (let [headers ["part" "e" "a" "v" "tx" "added"]
        cols (->> omit-cols
                  (set/difference (set headers))
                  (sort-by #(.indexOf headers %)))]
    (->> datoms
         (map
          (fn [{:keys [e a v tx added]}]
            (let [data {"part" (d/part e)
                        "e" (format id-format e)
                        "a" (d/ident db a)
                        "v" (if (nat-int? v)
                              (format id-format (-> db (d/pull [:db/id] v) :db/id))
                              (trunc v 24))
                        "tx" (format tx-id-format tx)
                        "added" added}]
              (select-keys data cols))))
         (pp/print-table cols))))

(defn select-keys-with-ns [data key-ns]
  (into {} (filter #(= (namespace (key %)) key-ns) data)))

(defn- resolve-ref [db m k v]
  (cond
    (and v (pos-int? v))
    (if-let [ident (d/ident db v)]
      (assoc m k ident)
      (assoc m k (wdb/pull db '[*] v)))
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

(def default-responses
  {bad-request {:schema {:errors ::wsc/error-response}}
   conflict {:schema {:conflict ::wsc/error-response}}
   precondition-failed {:schema ::wsc/error-response}})

(defn response-map [m]
  (into {} (map (fn [[rf sm]] [(:status (rf)) sm]) m)))

(defn conform-data [spec data & [names-validator]]
  (let [conformed (stc/conform spec
                               (if names-validator
                                 (names-validator data)
                                 data))]
    (if (s/invalid? conformed)
      (let [problems (expound-str spec data)]
        (throw (ex-info "Not valid according to spec."
                        {:problems problems
                         :type ::validation-error
                         :data data})))
      conformed)))
