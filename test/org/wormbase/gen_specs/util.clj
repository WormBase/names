(ns org.wormbase.gen-specs.util
  (:require
   [clojure.java.io :as io]
   [org.wormbase.db.schema :as owdbs]))

(defn load-seed-data []
  (owdbs/read-edn (io/resource "schema/seed-data.edn")))

(defn load-enum-samples [sd-ns]
  (->> (load-seed-data)
       (filter :db/ident)
       (filter #(= (-> % :db/ident namespace) sd-ns))))
