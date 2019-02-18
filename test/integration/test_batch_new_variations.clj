(ns integration.test-batch-new-variations
  (:require
   [clj-uuid :as uuid]
   [clojure.test :as t]
   [datomic.api :as d]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov elegans-ln]]
   [wormbase.db :as wdb]
   [wormbase.db-testing :as db-testing]
   [wormbase.fake-auth]
   [wormbase.names.batch :as wnb]
   [wormbase.names.service :as service]
   [wormbase.names.util :as wnu]
   [wormbase.names.variation :as wnv]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn new-variations [data]
  (api-tc/send-request "batch" :post data :sub-path "variation"))

(defn query-batch [db bid]
  (wnu/query-batch db bid wnv/info-pull-expr))

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
    (let [bdata (map #(array-map :variation/name (str "okay" %)) (range 1 21))
          [status body] (new-variations {:data bdata :prov basic-prov})]
      (t/is 201 (str status))
      (let [bid (get body :batch/id "")]
        (t/is (uuid/uuid-string? bid) (pr-str body))
        (let [batch (query-batch (d/db wdb/conn) (uuid/as-uuid bid))
              xs (map #(get-in % [:variation/status :db/ident]) batch)
              [info-status info-body] (api-tc/info "batch" bid)]
          (t/is (seq xs))
          (t/is (every? (partial = :variation.status/live) xs))
          (tu/status-is? 200 info-status info-body)
          (t/is (= (some-> info-body :provenance/what keyword)
                   :event/new-variation)))))))
