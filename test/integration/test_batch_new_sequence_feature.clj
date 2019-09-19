(ns integration.test-batch-new-sequence-feature
  (:require
   [clojure.test :as t]
   [clj-uuid :as uuid]
   [datomic.api :as d]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov]]
   [wormbase.db :as wdb]
   [wormbase.db-testing :as db-testing]
   [wormbase.names.batch.sequence-feature :as wnbsf]
   [wormbase.names.util :as wnu]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn new-sequence-features [data]
  (api-tc/send-request "batch" :post data :sub-path "sequence-feature"))

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [response (new-sequence-features {:data [] :prov nil})]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest single-item
  (t/testing "Batch with oen item accepted, returns batch id."
    (let [response (new-sequence-features {:data {:n 1} :prov basic-prov})]
      (t/is (ru-hp/created? response))
      (t/is (get-in response [:body :id] "") (pr-str response)))))

(t/deftest batch-success
  (t/testing "Batch of new sequence-features is processed successfully."
    (let [bdata {:n 20}
          response (new-sequence-features {:data bdata :prov basic-prov})]
      (t/is (ru-hp/created? response))
      (let [bid (get-in response [:body :id] "")]
        (t/is (uuid/uuid-string? bid) (pr-str response))
        (let [db (d/db wdb/conn)              
              batch (wnu/query-batch db (uuid/as-uuid bid) wnbsf/summary-pull-expr)
              xs (map #(get-in % [:sequence-feature/status :db/ident]) batch)
              response (api-tc/summary "batch" bid)]
          (t/is (seq xs))
          (t/is (every? (partial = :sequence-feature.status/live) xs))
          (t/is (ru-hp/ok? response))
          (t/is (= (some-> response :body :what keyword)
                   :event/new-sequence-feature)))))))


