(ns wormbase.gen-specs.util
  (:require
   [clojure.java.io :as io]
   [wormbase.util :as util]))

(defn load-seed-data []
  (-> (io/resource "schema/seed-data.edn")
      (util/read-edn)
      (:tx-data)))

(defn load-enum-samples [sd-ns]
  (->> (load-seed-data)
       (filter :db/ident)
       (filter #(= (-> % :db/ident namespace) sd-ns))))
