(ns schema.migrations.add-gene-splits
  (:require
   [clojure.java.io :as io]
   [datomic.api :as d]
   [clojure.string :as str]))

;; migration
;; ---------
;; iterate over all gene ids
;; for each gene id, look for :provenance/split-into ?e where ?e  == [:gene/id gid] == lur
;; build a list of gene refs.
(defn migrate [conn]
  (let [db (d/db conn)]
    (doseq [id-block (partition-all 200 (d/q '[:find [?gid ...] :where [_ :gene/id ?gid]] db))]
      (doseq [gene-id id-block]
        (let [splits (->> (d/q '[:find [?gid ...]
                                 :in $ ?e
                                 :where
                                 [?tx :provenance/split-from ?e]
                                 [?tx :provenance/split-into ?into]
                                 [?into :gene/id ?gid ?tx]]
                               (d/history db)
                               [:gene/id gene-id])
                          (map (partial conj [:gene/id]))
                          (map vec))]
          (when (seq splits)
            (print "Adding splits for" gene-id ": " (str/join #"," (map :gene/id splits)) " ...")
            (let [prov {:provenance/how :agent/console
                        :provenance/when (java.util.Date.)
                        :provenance/why "fixing up gene splits"
                        :provenance/person [:person/email "matthew.russell@wormbase.org"]
                        :provenance/what :event/split-gene}
                  lur [:gene/id gene-id]
                  dbid (-> db (d/entity lur) :db/id)
                  tx-data [[:db/add dbid :gene/splits splits] prov]
                  tx-res @(d/transact-async conn tx-data)]
              (if (:db-after tx-res)
                (println "OK")
                (println "FAILED")))))))))
