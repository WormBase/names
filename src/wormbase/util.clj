(ns wormbase.util
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
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

(defn sort-events-by
  "Sort a sequence of mappings representing events in temporal order."
  [k events & {:keys [most-recent-first]
               :or {most-recent-first false}}]
  (let [cmp (if most-recent-first
              #(compare %2 %1)
              #(compare %1 %2))]
    (sort-by k cmp events)))

(defn now
  "Return a java date converted from the current time in timezone `tz`."
  ([tz]
   (-> (jt/instant)
       (jt/zoned-date-time (jt/zone-id tz))
       (jt/to-java-date)))
  ([]
   (now "UTC")))

(defn datomic-internal-namespaces []
  (-> (read-app-config) :datomic :internal-namespaces set))

(defn discard-empty-valued-entries
  "Discard nil or blank values from a mapping."
  [data]
  (reduce-kv (fn [m k v]
               (if (or (nil? v)
                       (and (string? v) (str/blank? v))
                       ;; handle references like [:species/latin-name "C..."]
                       (and (vector? v) (nil? (second v))))
                 (dissoc m k)
                 m))
             data
             data))
