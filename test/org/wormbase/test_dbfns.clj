(ns org.wormbase.test-dbfns
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [org.wormbase.db-testing :as db-testing]

   [org.wormbase.db :as owdb]
   [org.wormbase.test-utils :as tu]
   [org.wormbase.specs.gene :as owsg]))

(t/use-fixtures :each db-testing/db-lifecycle)

(t/deftest test-merge-genes
  (t/testing "Merging Gene DB FN."
    (let [gene-refs (gen/sample (s/gen :provenance/merged-from) 2)
          gene-recs (gen/sample (s/gen ::owsg/update) 2)
          data-samples (->> (interleave gene-refs gene-recs)
                            (partition 2)
                            (map (partial apply merge)))
          uuu (prn data-samples)]
      (tu/with-fixtures
        (vec data-samples)
        (fn [conn db data prov]
          (let [db (owdb/db owdb/conn)
                invoke-mg (partial d/invoke db :wormbase.tx-fns/merge-genes)
                src (-> gene-refs first vec)
                target (-> gene-refs second vec)
                xxx (println "SRC:" src)
                yyy (println "TARGET:" target)]
            (invoke-mg db src target :gene/id)))))))

