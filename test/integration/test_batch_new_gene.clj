(ns integration.test-batch-update-gene
  (:require
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-response :refer [bad-request
                                    conflict
                                    created
                                    not-found not-found!
                                    ok]]
   [clj-uuid :as uuid]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.gen-specs.species :as gss]
   [wormbase.names.service :as service]
   [wormbase.names.batch :as wnb]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn new-genes [data]
  (api-tc/send-request "gene" :post data :sub-path "batch"))

(def elegans-ln "Caenorhabditis elegans")

(def basic-prov {:provenance/who {:person/email "tester@wormbase.org"}})

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [[status body] (new-genes {:data [] :prov nil})]
      (t/is (= (:status (bad-request)) status)))))

(t/deftest single-item
  (t/testing "Batch with one item accepted, returns batch id."
    (let [bdata [{:gene/sequence-name "MATTR.1"
                  :gene/species elegans-ln
                  :gene/biotype :biotype/cds}]
          [status body] (new-genes {:data bdata :prov basic-prov})]
      (t/is (= (:status (created)) status))
      (t/is (get-in body [:created :batch/id]) (pr-str body)))))

(t/deftest non-uniq-names
  (t/testing "Batch with multiple items, unique names is rejected."
    (let [bdata [{:gene/cgc-name "dup-1"
                  :gene/species elegans-ln}
                 {:gene/cgc-naem "dup-1"
                  :gene/species elegans-ln}]
          [status body] (new-genes {:data bdata :prov basic-prov})]
      (t/is (= (:status (bad-request)) status)))))

(t/deftest genes-invalid-species
  (t/testing "Batch with invalid species is rejected."
    (let [bdata [{:gene/cgc-name "dup-1"
                  :gene/species "Caenorhabditis donkey"}]
          [status body] (new-genes {:data bdata :prov basic-prov})]
      (t/is (= (:status (not-found)) status)))))

(t/deftest batch-success
  (t/testing "Batch with a random number of items is successful"
    (let [bdata [{:gene/cgc-name "okay-1"
                  :gene/species elegans-ln}
                 {:gene/sequence-name "OKIDOKE.1"
                  :gene/biotype :biotype/cds
                  :gene/species elegans-ln}]
          [status body] (new-genes {:data bdata :prov basic-prov})]
      (t/is (:status (created)) status)
      (let [bid (get-in body [:created :batch/id])]
        (t/is (uuid/uuid-string? bid))))))