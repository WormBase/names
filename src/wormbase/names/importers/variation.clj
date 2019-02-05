(ns wormbase.names.importers.variation
  (:require
   [wormbase.names.importers.processing :as wnip]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]))

(def ^{:doc "A mapping describing the format of the export files."}
  ;; no provenance exported for variations
  export-conf {:data {:header [:variation/id
                               :variation/name
                               :variation/status]}})


(s/def ::status (s/and string?
                       (s/or :variation.status/live (partial = "Live")
                             :variation.status/dead (partial = "Dead"))))

(def transact-batch (partial wnip/transact-batch :event/import))

(defn build-data-txes
  "Build the current entity representation of each variation."
  [tsv-path conf
   & {:keys [batch-size]
      :or {batch-size 500}}]
  (let [cast-fns {:variation/id (partial wnip/conformed :variation/id)
                  :variation/name (partial wnip/conformed :variation/name)
                  :variation/status (partial wnip/conformed-label ::status)}]
    (with-open [in-file (io/reader tsv-path)]
      (->> (wnip/parse-transform-cast in-file conf cast-fns)
           (map wnip/discard-empty-valued-entries)
           (partition-all batch-size)
           (doall)))))


(defn batch-transact-data [conn tsv-path]
  (let [conf (:data export-conf)]
    (doseq [batch (build-data-txes tsv-path conf)]
      @(transact-batch conn batch))))
  
(defn process
  [conn data-tsv-path & {:keys [n-in-flight]
                         :or {n-in-flight 10}}]
  (batch-transact-data conn data-tsv-path))
    


