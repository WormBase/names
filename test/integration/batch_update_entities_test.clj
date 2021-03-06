(ns integration.batch-update-entities-test
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov]]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.variation :as gsv]
   [wormbase.names.util :as wnu]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn update-variations [data]
  (api-tc/send-request "batch" :put data :sub-path "entity/variation"))

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [response (update-variations {:data [] :prov nil})]
      (t/is (ru-hp/bad-request? response)))))

(defn make-fixtures []
  (let [ids (gen/sample gsv/id 2)
        names (gen/sample gsv/name 2)
        stati (repeat 2 :variation.status/live)]
    (map #(zipmap [:variation/id :variation/name :variation/status] [%1 %2 %3])
         ids names stati)))

(t/deftest non-uniq-names
  (t/testing "Batch with multiple items containing non-unique names is rejected."
    (let [fixtures (make-fixtures)]
      (tu/with-variation-fixtures
        fixtures
        (fn [_]
          (let [[f1 f2] fixtures
                bdata (map #(wnu/unqualify-keys % "variation")
                           [(select-keys f1 [:variation/id :variation/name])
                            (merge (select-keys f2 [:variation/id])
                                   (select-keys f1 [:variation/name]))])
                response (update-variations {:data bdata :prov basic-prov})]
            (t/is (ru-hp/conflict? response))))))))

(t/deftest invalid-name
  (t/testing "Batch containing invlaid name is rejected."
    (let [fixtures (make-fixtures)]
      (tu/with-variation-fixtures
        fixtures
        (fn [_]
          (let [[f1 f2] fixtures
                bdata (map #(wnu/unqualify-keys % "variation")
                           [(select-keys f1 [:variation/id :variation/name])
                            (merge (select-keys f2 [:variation/id])
                                   (select-keys f1 [:variation/name]))])
                response (update-variations {:data bdata :prov basic-prov})]
            (t/is (ru-hp/conflict? response))))))))

