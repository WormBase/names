(ns wormbase.ids.core
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.walk :as w]
   [datomic.api :as d]))

(defn attr-schema-unique? [db attr]
  (try
    (let [s-attr (:db/unique (d/entity db attr))]
      (#{:db.unique/identity :db.unique/value} s-attr))
    (catch IllegalArgumentException iae)))

(defn entids->data [db m k v]
    (assoc m
           k
           (cond
             (or (pos-int? v) (vector? v))
             (let [prs (d/pull db '[*] v)
                   uniq-keys (filter (fn [[a v]]
                                          (attr-schema-unique? db a)) prs)]
               (select-keys prs (first uniq-keys)))
             :else v)))

(defn data->entid [db m k v]
  (assoc m
         k
         (cond
           (and (map? v) (:db/id v)) (:db/id v)
           (keyword? v) [:db/ident v]
           (pos-int? v) (:db/id (d/pull db '[*] v))
           (vector? v) v
           :else v)))

(defn data->entids
  "Resolve datomic db entids to data in entity-map `em`."
  [db em]
  (w/postwalk
      (fn id-resolve [xs]
        (if (map? xs)
          (reduce-kv (partial data->entid db) {} xs)
          xs))
      em))

(defn cas-batch
  "Collate a collection of compare-and-swap operations."
  [db eid data]
  (let [entity-map (d/pull db '[*] eid)]
    (some->> data
             (data->entids db)
             (map (fn gen-cas [[k v]]
                    (let [e-data (data->entids db entity-map)
                          old-v (k e-data)]
                      (when-not (or (nil? v)
                                    (= old-v v))
                        [:db/cas eid k old-v v]))))
             (remove nil?))))

(defn latest-id
  "Get the latest identifier for a given `ident`."
  [db ident]
  (d/q '[:find (max ?gid) .
         :in $ ?ident
         :where
         [?e ?ident ?gid]]
       (d/history db)
       ident))

(s/fdef parse-digits
  :args (s/cat :identifier string?)
  :ret (s/nilable int?)
  :fn #(str/includes? (:args %) (-> % :ret str)))
(defn parse-digits
  "Parse digits from an identifier."
  [identifier]
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

(defn identifier-format [db uiident]
  (let [{template :template/format} (d/pull db
                                            '[:template/format]
                                            [:template/describes uiident])]
    template))

(defn new
  "Asssign new monitonically increasing identifiers in collections of mappings.

  `template` - A c-style template string that can be used to format identifiers for a `uiident`.
  `uiident` - A datomic ident that uniquely identifies an entity.
  `coll` - A collection of mappings that contain data to be transacted.
           Maps in this collection will be augmented with identifiers associated with `uiident`.

  Returns data suitable for passing to `datomic.api/transact`."
  [db template uiident coll]
  (let [start-n (latest-id-number db uiident)
        stop-n (+ (count coll) start-n)]
    (some->> (range start-n stop-n)
             (map inc)
             (map (partial format template))
             (map (partial array-map uiident))
             (interleave coll)
             (partition 2)
             (map (partial apply merge)))))
