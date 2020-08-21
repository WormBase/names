(ns integration.batch-new-entities-test
  (:require
   [clj-uuid :as uuid]
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov]]
   [wormbase.db :as wdb]
   [wormbase.db-testing :as db-testing]
   [wormbase.fake-auth]
   [wormbase.names.util :as wnu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn new-variations [data]
  (api-tc/send-request "batch" :post data :sub-path "entity/variation"))

(defn new-sequence-feature [data]
  (api-tc/send-request "batch" :post data :sub-path "entity/sequence-feature"))


(defn query-batch [db bid entity-type]
  (let [status-attr (keyword entity-type "status")
        pull-expr (conj '[*] {status-attr [:db/ident]})]
    (wnu/query-batch db bid #(d/pull %1 pull-expr %2))))

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

(t/deftest batch-success-named
  (t/testing "Batch of new named entities successful"
    (let [bdata (map #(array-map :name (str "okay" %)) (range 1 11))
          response (new-variations {:data bdata :prov basic-prov})]
      (t/is (ru-hp/created? response) (-> response :status str))
      (let [bid (get-in response [:body :id] "")]
        (t/is (uuid/uuid-string? bid) (pr-str response))
        (let [batch (query-batch (d/db wdb/conn) (uuid/as-uuid bid) "variation")
              xs (->> batch
                      (remove :counter/variation)
                      (map #(get-in % [:variation/status :db/ident])))
              response2  (api-tc/summary "batch" bid)]
          (t/is (seq xs))
          (t/is (every? (partial = :variation.status/live) xs)
                (pr-str xs))
          (t/is (ru-hp/ok? response2))
          (t/is (= "new-variation"
                   (some-> response2 :body :what))
                (pr-str (:body response2))))))))

(t/deftest batch-success-un-named
  (t/testing "Batch of new un-named entities is successful"
    (let [bdata {:n 10}
          response (new-sequence-feature {:data bdata :prov basic-prov})]
      (t/is (ru-hp/created? response) (-> response :status str))
      (let [bid (get-in response [:body :id] "")]
        (t/is (uuid/uuid-string? bid) (pr-str response))
        (let [batch (query-batch (d/db wdb/conn) (uuid/as-uuid bid) "sequence-feature")
              xs (->> batch
                      (remove :counter/sequence-feature)
                      (map #(get-in % [:sequence-feature/status :db/ident])))
              response2  (api-tc/summary "batch" bid)]
          (t/is (seq xs))
          (t/is (every? (partial = :sequence-feature.status/live) xs))
          (t/is (ru-hp/ok? response2))
          (t/is (= "new-sequence-feature"
                   (some-> response2 :body :what))
                (pr-str (:body response2))))))))
