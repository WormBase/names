(ns wormbase.util
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.walk :as w]
   [datomic.api :as d]
   [aero.core :as aero]
   [java-time :as jt]
   [wormbase.ids.core :as wic])
  (:import
   (java.io PushbackReader)
   (java.util Date)))

(defn read-edn [readable]
  (let [edn-read (partial edn/read {:readers *data-readers*})]
    (-> readable
        io/reader
        (PushbackReader.)
        (edn-read))))

(defn read-app-config
  ([]
   (read-app-config "config.edn"))
  ([resource-filename]
   (aero/read-config (io/resource resource-filename))))

(defn elide-db-internals
  "Remove datomic internal attribute/value pairs from a seq of maps."
  [db data]
  (w/postwalk (fn presenter [m]
                (cond
                  (and (map? m) (:db/ident m)) (:db/ident m)
                  (and (map? m)
                       (= (count m) 1)
                       (wic/attr-schema-unique? db (-> m first key))) (-> m first val)
                  (map? m) (dissoc m :db/id :db/txInstant)
                  :default m))
              data))

(def ^{:deprecated-in 0.6} undatomicize elide-db-internals)

(defn elide-importer-info
  [data]
  (reduce-kv (fn masker [m k v]
               (if (= (namespace k) "importer")
                 (dissoc m k v)
                 (assoc m k v)))
             (empty data)
             data))

(defn days-ago [n]
  (-> (jt/instant)
      (jt/minus (jt/days n))
      (jt/to-java-date)))

(defn format-java-date [dt & {:keys [tz fmt]
                              :or {tz (jt/zone-id)
                                   fmt :iso-date-time}}]
  {:pre [(instance? Date dt)]}
  (jt/format fmt (-> dt
                     (jt/instant)
                     (jt/zoned-date-time tz))))

(defn zoned-date-time? [dt]
  (try
    (jt/zoned-date-time dt)
    (catch Exception e)))

(defn sort-events-by
  "Sort a sequence of mappings representing events in temporal order."
  [k events & {:keys [most-recent-first]
               :or {most-recent-first false}}]
  (let [cmp (if most-recent-first
              #(compare %2 %1)
              #(compare %1 %2))]
    (sort-by k cmp events)))

(defn datomic-internal-namespaces []
  (-> (read-app-config) :datomic :internal-namespaces set))
