(ns org.wormbase.names.util
  (:require [clojure.walk :as walk]))


(defn- nsify [domain kw]
  (if (namespace kw)
    kw
    (keyword domain (name kw))))

(defn namespace-keywords
  "Add namespaces to keys in `input-data` mapping.

  Used to setup data to be consistent for specs without requiring
  input (that typically comes from JSON) to use qualified namespaces."
  [domain data]
  (map #(reduce-kv (fn [rec kw v]
                     (-> rec
                         (dissoc kw)
                         (assoc (nsify domain kw) v)))
                   (empty %)
                   %)
       data))
