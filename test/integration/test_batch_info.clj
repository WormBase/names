(ns integration.test-batch-info
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as str]
   [clojure.test :as t]
   [datomic.api :as d]
   [integration.test-batch-new-gene :refer [basic-prov elegans-ln new-genes]]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn info [bid]
  (api-tc/info "batch" (str "gene/" bid)))

(t/deftest batch-id-missing
  (t/testing "When a batch ID is not stored."
    (let [[status body] (info (d/squuid))]
      (tu/status-is? 404 status body))))

(t/deftest batch-id-invalid
  (t/testing "When a batch ID is not of the correct/expected format."
    (let [[status body] (info "zxx")]
      (tu/status-is? 400 status body))))

(t/deftest info-success
  (t/testing "Retrieving info about at batch working (provenance only atm)."
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
          (let [[status body] (info bid)]
            (tu/status-is? 200 status body)
            (t/is (map? body))
            (t/is (str/includes? (get-in body [:provenance/who] "") "@")
                  (pr-str body))))))))
