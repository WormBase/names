(ns integration.test-batch-new-variations
  (:require
   [clojure.test :as t]
   [datomic.api :as d]
   [clj-uuid :as uuid]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db :as wdb]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.gen-specs.species :as gss]
   [wormbase.names.service :as service]
   [wormbase.names.batch :as wnb]
   [wormbase.test-utils :as tu]
   [wormbase.fake-auth]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn new-variations [data]
  (api-tc/send-request "batch" :post data :sub-path "variation"))

(def elegans-ln "Caenorhabditis elegans")

(def basic-prov {:provenance/who {:person/email "tester@wormbase.org"}})

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [[status body] (new-variations {:data [] :prov nil})]
      (t/is (= 400 status)))))

(t/deftest single-item
  (t/testing "Batch with one item accepted, returns batch id."
    (let [bdata [{:variation/name "abc1"}]
          [status body] (new-variations {:data bdata :prov basic-prov})]
      (t/is (= 201 status))
      (t/is (get body :batch/id "") (pr-str body)))))

(t/deftest non-uniq-names
  (t/testing "Batch with multiple items, unique names is rejected."
    (let [bdata [{:variation/name "abc1"}
                 {:variation/name "abc1"}]
          [status body] (new-variations {:data bdata :prov basic-prov})]
      (tu/status-is? 409 status body))))
