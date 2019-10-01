(ns integration.batch-new-entities-test
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
  (api-tc/send-request "batch" :post data :sub-path "entity/variation"))

(defn query-batch [db bid]
  (wnu/query-batch db bid wnv/summary-pull-expr))

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [response (new-variations {:data [] :prov nil})]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest single-item
  (t/testing "Batch with one item accepted, returns batch id."
    (let [bdata [{:name "abc1"}]
          response (new-variations {:data bdata :prov basic-prov})]
      (t/is (ru-hp/created? response))
      (t/is (get-in response [:body :id] "") (pr-str response)))))

(t/deftest non-uniq-names
  (t/testing "Batch with multiple items, unique names is rejected."
    (let [bdata [{:name "abc1"}
                 {:name "abc1"}]
          response (new-variations {:data bdata :prov basic-prov})]
      (t/is (ru-hp/conflict? response)))))

(t/deftest batch-success
  (t/testing "Batch of new vaiations successful"
    (let [bdata (map #(array-map :name (str "okay" %)) (range 1 21))
          response (new-variations {:data bdata :prov basic-prov})]
      (t/is (ru-hp/created? response) (-> response :status str))
      (let [bid (get-in response [:body :id] "")]
        (t/is (uuid/uuid-string? bid) (pr-str response))
        (let [batch (query-batch (d/db wdb/conn) (uuid/as-uuid bid))
              xs (map #(get-in % [:variation/status :db/ident]) batch)
              response2  (api-tc/summary "batch" bid)]
          (t/is (seq xs))
          (t/is (every? (partial = :variation.status/live) xs))
          (t/is (ru-hp/ok? response2))
          (t/is (= (some-> response2 :body :what keyword)
                   :event/new-variation)))))))
