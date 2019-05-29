(ns integration.test-batch-update-gene
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.test :as t]
   [clj-uuid :as uuid]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov elegans-ln]]
   [wormbase.db-testing :as db-testing]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn update-genes [data]
  (api-tc/send-request "batch" :put data :sub-path "gene"))

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [response (update-genes {:data [] :prov nil})]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest non-uniq-names
  (t/testing "Batch with multiple items containing non-unique names is rejected."
    (let [fixtures (tu/gene-samples 2)
          [g1 g2] fixtures]
      (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (let [bdata [{:gene/id (:gene/id g1)
                        :gene/cgc-name "dup-1"}
                       {:gene/id (:gene/id g2)
                        :gene/cgc-name "dup-1"}]
                response (update-genes {:data bdata :prov basic-prov})]
            (t/is (ru-hp/bad-request? response))))))))

(t/deftest invalid-cgc-name
  (t/testing "Errors are reported when provided with a invlaid CGC name"
    (let [fixtures (tu/gene-samples 2)
          [g1 g2] fixtures]
      (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (let [bdata [{:gene/id (:gene/id g1)
                        :gene/cgc-name "invalid_CGC_NAME"}]
                response (update-genes {:data bdata :prov basic-prov})]
            (t/is (ru-hp/bad-request? response))))))))

(t/deftest invalid-sequence-name
  (t/testing "Errors are reported when provided with a invlaid Sequence name"
    (let [fixtures (tu/gene-samples 2)
          [g1 g2] fixtures]
      (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (let [bdata [{:gene/id (:gene/id g1)
                        :gene/sequence-name "invalid-sequence-name.1"}]
                response (update-genes {:data bdata :prov basic-prov})]
            (t/is (ru-hp/bad-request? response))))))))

(t/deftest genes-invalid-species
  (t/testing "Batch with invalid species is rejected."
    (let [fixtures (tu/gene-samples 1)
          gid (-> fixtures first :gene/id)
          bad-species "Caenorhabditis donkey"]
      (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (let [bdata [{:gene/cgc-name "dup-1"
                        :gene/species bad-species
                        :gene/id gid}]
                response (update-genes {:data bdata :prov basic-prov})]
            (t/is (ru-hp/bad-request? response))
            (t/is (some (fn [error]
                          (str/includes? error bad-species))
                        (get-in response [:body :errors] []))
                  (pr-str response))))))))

(t/deftest single-item
  (let [fixtures (tu/gene-samples 1)
        gene-rec (first fixtures)]
    (tu/with-gene-fixtures
      fixtures
      (fn [conn]
        (t/testing "Batch with one item accepted, returns batch id."
          (let [species (-> gene-rec :gene/species second)
                bdata [(merge {:gene/species species
                               :gene/cgc-name (tu/cgc-name-for-species species)}
                              (find gene-rec :gene/id))]
                payload {:data bdata :prov basic-prov}
                response (update-genes payload)]
            (t/is (ru-hp/ok? response))
            (t/is (get-in response [:body :updated :batch/id]) (pr-str response))))))))

(t/deftest multi-item
  (t/testing "Batch with a random number of items is successful"
    (let [[g1 g2] (tu/gene-samples 2)
          species (-> g1 :gene/species second)
          bdata [{:gene/cgc-name (tu/cgc-name-for-species species)
                  :gene/id (:gene/id g1)
                  :gene/species species}
                 {:gene/sequence-name "OKY.1"
                  :gene/biotype :biotype/cds
                  :gene/id (:gene/id g2)
                  :gene/species elegans-ln}]]
      (tu/with-gene-fixtures
        [g1 g2]
        (fn [conn]
          (let [response (update-genes {:data bdata :prov basic-prov})]
            (t/is (ru-hp/ok? response) (pr-str response))
            (let [bid (get-in response [:body :updated :batch/id] "")]
              (t/is (uuid/uuid-string? bid)))))))))
