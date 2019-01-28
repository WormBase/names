(ns integration.test-batch-info
  (:require
   [clojure.test :as t]
   [ring.util.http-response :refer [bad-request conflict not-found ok]]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [integration.test-batch-new-gene :refer [basic-prov elegans-ln new-genes]]
   [wormbase.test-utils :as tu]
   [datomic.api :as d]
   [wormbase.gen-specs.gene :as gsg]
   [clojure.spec.gen.alpha :as gen]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn info [bid]
  (api-tc/info "batch"(str "info/" bid)))

(t/deftest missing-batch-id
  (t/testing "When a batch ID doesn't exist."
    (let [[status body] (info "zxx")]
      (tu/status-is? status (:status (not-found)) body))))

(t/deftest success
  (t/testing "Retrieving batch info."
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
          (tu/gene-provenance data :batch-id bid))
        samples
        (fn [conn]
          (let [[status body] (info bid)]
            (tu/status-is? status (:status (ok)) body)
            (t/is (empty? body) (pr-str body))))))))
