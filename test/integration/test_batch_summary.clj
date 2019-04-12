(ns integration.test-batch-summary
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as str]
   [clojure.test :as t]
   [datomic.api :as d]
   [integration.test-batch-new-gene :refer [new-genes]]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.constdata :refer [basic-prov elegans-ln]]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn summary [bid]
  (api-tc/summary "batch" bid))

(t/deftest batch-id-missing
  (t/testing "When a batch ID is not stored."
    (let [[status body] (summary (d/squuid))]
      (t/is (ru-hp/not-found? {:status status :body body})))))

(t/deftest batch-id-invalid
  (t/testing "When a batch ID is not of the correct/expected format."
    (let [[status body] (summary "zxx")]
      (t/is (ru-hp/bad-request? {:status status :body body}))))) 

(t/deftest summary-success
  (t/testing "Retrieving summary about at batch working (provenance only atm)."
    (let [gene-ids (gen/sample gsg/id 2)
          samples [{:gene/cgc-name "okay-1"
                    :gene/species elegans-ln
                    :gene/id (first gene-ids)}
                   {:gene/sequence-name "OKIDOKE.1"
                    :gene/biotype :biotype/cds
                    :gene/species elegans-ln
                    :gene/id (second gene-ids)}]
          bid (d/squuid)]
      (tu/with-batch-fixtures
        tu/gene-sample-to-txes
        (fn [data]
          (tu/provenance data :batch-id bid))
        samples
        (fn [conn]
          (let [[status body] (summary bid)]
            (t/is (ru-hp/ok? {:status status :body body}))
            (t/is (map? body))
            (t/is (str/includes? (get-in body [:provenance/who] "") "@")
                  (pr-str body))))))))
