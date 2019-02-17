(ns integration.test-batch-new-variations
  (:require
   [clojure.test :as t]
   [datomic.api :as d]
   [clj-uuid :as uuid]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov elegans-ln]]
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

(t/deftest batch-success
  (t/testing "Batch of new vaiations successful"
    (let [bdata (map #(array-map :variation/name (str "okay" n)) (range 20))
          [status body] (new-variations {:data bdata :prov basic-prov})]
      (t/is 201 (str status))
      (let [bid (get body :batch/id "")]
        (t/is (uuid/uuid-string? bid) (pr-str body))
        (let [batch (tu/query-gene-batch (d/db wdb/conn) (uuid/as-uuid bid))
              xs (map #(get-in % [:variation/status :db/ident]) batch)
              [info-status info-body] (api-tc/info "batch" bid)]
          (t/is (seq xs))
          (t/is (every? (partial = :variation.status/live) xs))
          (tu/status-is? 200 info-status info-body)
          (t/is (= (some-> info-body :provenance/what keyword)
                   :event/new-variation)))))))
