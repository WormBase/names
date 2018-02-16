(ns org.wormbase.names.util
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.walk :as walk]))

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

(defn entity->map [ent]
  (walk/prewalk #(if (instance? datomic.query.EntityMap %)
                   (into {} %)
                   %)
                ent))
