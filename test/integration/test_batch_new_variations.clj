(ns integration.test-batch-new-variations
  (:require
   [clj-uuid :as uuid]
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-predicates :as ru-hp]
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
  (wnu/query-batch db bid wnv/summary-pull-expr))

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [[status body] (new-variations {:data [] :prov nil})]
      (t/is (ru-hp/bad-request? {:status status :body body})))))

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
      (t/is (ru-hp/conflict? {:status status :body body})))))

(t/deftest batch-success
  (t/testing "Batch of new vaiations successful"
    (let [bdata (map #(array-map :variation/name (str "okay" %)) (range 1 21))
          [status body] (new-variations {:data bdata :prov basic-prov})]
      (t/is (ru-hp/created? {:status status :body body}) (str status))
      (let [bid (get body :batch/id "")]
        (t/is (uuid/uuid-string? bid) (pr-str body))
        (let [batch (query-batch (d/db wdb/conn) (uuid/as-uuid bid))
              xs (map #(get-in % [:variation/status :db/ident]) batch)
              [summary-status summary-body] (api-tc/summary "batch" bid)]
          (t/is (seq xs))
          (t/is (every? (partial = :variation.status/live) xs))
          (t/is (ru-hp/ok? {:status summary-status :body summary-body}))
          (t/is (= (some-> summary-body :provenance/what keyword)
                   :event/new-variation)))))))
