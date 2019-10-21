(ns wormbase.names.validation
  (:require
   [clojure.spec.alpha :as s]
   [datomic.api :as d]))

(defmulti validate-names (fn [request data]
                           (some->> data
                                    (keys)
                                    (filter qualified-keyword?)
                                    (first)
                                    (namespace)
                                    (keyword))))
