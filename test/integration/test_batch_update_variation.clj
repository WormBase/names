(ns integration.test-batch-update-variation
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.variation :as gsv]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn update-variations [data]
  (api-tc/send-request "batch" :put data :sub-path "variation"))

(def basic-prov {:provenance/who {:person/email "tester@wormbase.org"}})

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [[status body] (update-variations {:data [] :prov nil})]
      (t/is (= 400 status)))))

(t/deftest non-uniq-names
  (t/testing "Batch with multiple items containing non-unique names is rejected."
    (let [ids (gen/sample gsv/id 2)
          names (gen/sample gsv/name 2)
          stati (repeat 2 :variation.status/live)
          fixtures (map #(zipmap [:variation/id :variation/name :variation/status] [%1 %2 %3])
                        ids names stati)
          [f1 f2] fixtures]
      (tu/with-fixtures
        fixtures
        (fn [conn]
          (let [bdata [(select-keys f1 [:variation/id :variation/name])
                       (merge (select-keys f2 [:variation/id])
                              (select-keys f1 [:variation/name]))]
                [status body] (update-variations {:data bdata :prov basic-prov})]
            (t/is (= 409 status) (pr-str body))))))))
