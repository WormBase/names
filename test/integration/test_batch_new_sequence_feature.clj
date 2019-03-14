(ns integration.test-batch-new-sequence-feature
  (:require
   [clojure.test :as t]
   [clj-uuid :as uuid]
   [datomic.api :as d]
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
    (let [[status body] (new-sequence-features {:data [] :prov nil})]
      (t/is (= 400 status)))))

(t/deftest single-item
  (t/testing "Batch with oen item accepted, returns batch id."
    (let [[status body] (new-sequence-features {:data {:n 1} :prov basic-prov})]
      (t/is (= 201 status) (pr-str body))
      (t/is (get body :batch/id "") (pr-str body)))))

(t/deftest batch-success
  (t/testing "Batch of new sequence-features is processed successfully."
    (let [bdata {:n 20}
          [status body] (new-sequence-features {:data bdata :prov basic-prov})]
      (t/is 201 (str status))
      (let [bid (get body :batch/id "")]
        (t/is (uuid/uuid-string? bid) (pr-str body))
        (let [db (d/db wdb/conn)              
              batch (wnu/query-batch db (uuid/as-uuid bid) wnbsf/summary-pull-expr)
              xs (map #(get-in % [:sequence-feature/status :db/ident]) batch)
              [summary-status summary-body] (api-tc/summary "batch" bid)]
          (t/is (seq xs))
          (t/is (every? (partial = :sequence-feature.status/live) xs))
          (tu/status-is? 200 summary-status summary-body)
          (t/is (= (some-> summary-body :provenance/what keyword)
                   :event/new-sequence-feature)))))))


