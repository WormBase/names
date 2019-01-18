(ns integration.test-batch-update-gene
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.test :as t]
   [clj-uuid :as uuid]
   [ring.util.http-response :refer [bad-request conflict not-found ok]]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn update-genes [data]
  (api-tc/send-request "batch" :put data :sub-path "gene"))

(def basic-prov {:provenance/who {:person/email "tester@wormbase.org"}})

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [[status body] (update-genes {:data [] :prov nil})]
      (t/is (= (:status (bad-request)) status)))))

(t/deftest non-uniq-names
  (t/testing "Batch with multiple items containing non-unique names is rejected."
    (let [fixtures (tu/gene-samples 2)
          [g1 g2] fixtures]
      (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (let [bdata [{:gene/species (-> g1 :gene/species second)
                        :gene/id (:gene/id g1)
                        :gene/cgc-name "dup-1"}
                       {:gene/species (-> g2 :gene/species second)
                        :gene/id (:gene/id g2)
                        :gene/cgc-name "dup-1"}]
                [status body] (update-genes {:data bdata :prov basic-prov})]
            (t/is (= (:status (conflict)) status) (pr-str body))))))))


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
                [status body] (update-genes {:data bdata :prov basic-prov})]
            (tu/status-is? (:status (conflict)) status body)
            (t/is (some (fn [error]
                          (and error
                               (str/includes? (str/lower-case error) "does not exist")
                               (str/includes? error bad-species)))
                        (get body :errors [])))))))))

(t/deftest single-item
  (let [fixtures (tu/gene-samples 1)
        gene-rec (first fixtures)]
    (tu/with-gene-fixtures
      fixtures
      (fn [conn]
        (t/testing "Batch with one item accepted, returns batch id."
          (let [bdata [(merge {:gene/species (-> gene-rec :gene/species second)
                               :gene/cgc-name "chg-1"}
                              (find gene-rec :gene/id))]
                payload {:data bdata :prov basic-prov}
                [status body] (update-genes payload)]
            (tu/status-is? (:status (ok)) status body)
            (t/is (get-in body [:updated :batch/id]) (pr-str body))))))))

(t/deftest multi-item
  (t/testing "Batch with a random number of items is successful"
    (let [[g1 g2] (tu/gene-samples 2)
          bdata [{:gene/cgc-name "okay-1"
                  :gene/id (:gene/id g1)
                  :gene/species (-> g1 :gene/species second)}
                 {:gene/sequence-name "OKIDOKE.1"
                  :gene/biotype :biotype/cds
                  :gene/id (:gene/id g2)
                  :gene/species (-> g2 :gene/species second)}]]
      (tu/with-gene-fixtures
        [g1 g2]
        (fn [conn]
          (let [[status body] (update-genes {:data bdata :prov basic-prov})]
            (tu/status-is? (:status (ok)) status body)
            (let [bid (get-in body [:updated :batch/id] "")]
              (t/is (uuid/uuid-string? bid)))))))))
