(ns wormbase.names.util
  (:require
   [clojure.pprint :as pp]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.walk :as w]
   [buddy.core.codecs :as codecs]
   [datomic.api :as d]
   [expound.alpha :refer [expound-str]]
   [phrase.alpha :as ph]
   [ring.util.http-response :refer [bad-request conflict not-found not-modified ok]]
   [ring.util.response :refer [header]]
   [wormbase.db :as wdb]
   [wormbase.specs.common :as wsc]
   [wormbase.specs.validation :as wsv]))

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
    :else
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
  (some #(= status (name %1))
        (filter keyword? ((juxt identity :db/ident) ent))))

(def live? (partial has-status? "live"))

(def dead? (partial has-status? "dead"))

(def not-live? #(not (live? %)))

(def ^{:doc (str "The set of default responses used to generate swagger documentation "
                 "for compojure.api endpoint definitions.")}
  default-responses
  {bad-request {:schema {:errors ::wsv/error-response}
                :description "The request input data was found to be invalid."}
   conflict {:schema {:conflict ::wsc/error-response}
             :description "Processing the request data caused a conflict with an existing resource."}
   not-found {:schema {:missing ::wsc/error-response}
              :description "An entity referred to in the request data is missing in the database."}})

(defn response-map
  ([m]
   (into {} (map (fn [[rf sm]] [(:status (rf)) sm]) m)))
  ([k v & kvs]
   (response-map (apply (partial assoc default-responses k v) kvs))))


(defn http-responses-for-read [swagger-schema]
  (-> default-responses
      (assoc ok swagger-schema)
      (assoc not-modified {:schema map?
                           :description "The content has not changed since it was last requested."
                           :headers {:etag string?
                                     :if-none-match string?}})
      (dissoc bad-request)
      (dissoc conflict)
      (response-map)))

(defn conform-data [spec data]
  (let [conformed (s/conform spec data)]
    (if (s/invalid? conformed)
      (let [problems (expound-str spec data)
            msg (ph/phrase-first {} spec data)]
        (throw (ex-info msg
                        {:problems problems
                         :type :user/validation-error
                         :data data})))
      conformed)))

(defn conform-data-drop-label [spec data]
  (let [cdata (conform-data spec data)]
    (second cdata)))

(defn query-batch [db bid query-info-fn]
  (map #(query-info-fn db %)
       (d/q '[:find [?e ...]
              :in $ ?bid
              :where
              [?tx :batch/id ?bid]
              [?e ?a ?v ?tx]
              [(not= ?e ?tx)]
              [?a :db/ident ?aname]]
            db
            bid)))

(defn encode-etag [latest-t]
  (some-> latest-t str codecs/str->bytes codecs/bytes->b64 codecs/bytes->str))

(defn add-etag-header-maybe [response etag]
  (if (seq etag)
    (header response "etag" etag)
    response))

(defn qualify-keys
  "Transform `mapping` such all non-qualfied keys have the namespace `entity-type` applied."
  [mapping entity-type & {:keys [skip-keys]
                          :or {skip-keys #{}}}]
  (reduce-kv (fn [m k v]
               (if (skip-keys k)
                 m
                 (if (simple-keyword? k)
                   (assoc m (keyword entity-type (name k)) v)
                   (if-not (contains? m k)
                     (assoc m k v)
                     m))))
             {}
             mapping))

(defn unqualify-keys
  "Transform `mapping` such that all qualfied keys with namespace `entity-type` are unqualfied."
  [mapping entity-type]
  (reduce-kv (fn [m k v]
               (if (and (qualified-keyword? k)
                        (= (namespace k) entity-type))
                 (assoc m (-> k name keyword) v)
                 (assoc m k v)))
             {}
             mapping))

(defn transform-ident-ref [k m kw-ns]
  (update m k (fn [old]
                (keyword kw-ns (if (keyword old)
                                 (name old)
                                 old)))))
(defn unqualify-maybe [x]
  (if (qualified-keyword? x)
    (name x)
    x))

(defn phrase-all
  "Phrase all problems.
  Takes a context a clojure.spec problem and dispatches to a phraser.
  Returns a sequence of maps containing return value for each phraser matching a problem
  under they key named `reason`."
  ([context spec value]
   (phrase-all context spec value #{:cloned :uncloned}))
  ([context spec value include-context-topics]
   (some->> (s/explain-data spec value)
            ::s/problems
            (map (fn explain-spec [prob]
                   (let [last-path-seg (some-> prob :path last)]
                     (if (and last-path-seg
                              (keyword? last-path-seg)
                              (include-context-topics last-path-seg))
                       (let [reason (str/join " " [(ph/phrase {} prob)
                                                   "for a"
                                                   (name last-path-seg)
                                                   (:topic context)])]
                         {:reason reason})
                       {:reason (ph/phrase {} prob)})))))))

