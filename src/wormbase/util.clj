(ns wormbase.util
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.walk :as w]
   [datomic.api :as d]
   [wormbase.ids.core :as wic])
  (:import
   (java.io PushbackReader)))

 (defn read-edn [readable]
  (let [edn-read (partial edn/read {:readers *data-readers*})]
    (-> readable
        io/reader
        (PushbackReader.)
        (edn-read))))

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
