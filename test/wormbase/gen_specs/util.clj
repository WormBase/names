(ns wormbase.gen-specs.util
  (:require
   [clojure.java.io :as io]
   [wormbase.util :as util]))

(defn load-seed-data []
  (util/read-edn (io/resource "schema/seed-data.edn")))

(defn load-enum-samples [sd-ns]
  (->> (load-seed-data)
       (filter :db/ident)
       (filter #(= (-> % :db/ident namespace) sd-ns))))
