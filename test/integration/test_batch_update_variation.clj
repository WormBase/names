(ns integration.test-batch-update-variation
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov]]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.variation :as gsv]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn update-variations [data]
  (let [[status body] (api-tc/send-request "batch" :put data :sub-path "variation")]
    {:status status :body body}))

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
      (tu/with-fixtures
        fixtures
        (fn [conn]
          (let [[f1 f2] fixtures
                bdata [(select-keys f1 [:variation/id :variation/name])
                       (merge (select-keys f2 [:variation/id])
                              (select-keys f1 [:variation/name]))]
                {:keys [status body] :as response} (update-variations {:data bdata :prov basic-prov})]
            (t/is (ru-hp/conflict? response))))))))

(t/deftest invalid-name
  (t/testing "Batch containing invlaid name is rejected."
    (let [fixtures (make-fixtures)]
      (tu/with-fixtures
        fixtures
        (fn [conn]
          (let [[f1 f2] fixtures
                bdata [(select-keys f1 [:variation/id :variation/name])
                       (merge (select-keys f2 [:variation/id])
                              (select-keys f1 [:variation/name]))]
                response (update-variations {:data bdata :prov basic-prov})]
            (t/is (ru-hp/conflict? response))))))))


