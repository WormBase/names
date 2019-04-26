(ns integration.test-batch-retract-gene-cgc-names
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [clj-uuid :as uuid]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov elegans-ln]]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.gen-specs.variation :as gsv]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn retract-gene-cgc-name [data]
  (let [data* (assoc data :batch-size 1)]
    (api-tc/send-request "batch" :delete data* :sub-path "gene/cgc-name")))

(defn retract-variation-name [data]
  (let [data* (assoc data :batch-size 1)]
    (api-tc/send-request "batch" :delete data* :sub-path "variation/name")))

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (doseq [retract-fn [retract-gene-cgc-name
                        retract-variation-name]]
      (let [[status body] (retract-fn {:data [] :prov nil})]
        (t/is (ru-hp/bad-request? {:status status :body body}))))))

(t/deftest batch-retract-gene-cgc-name-success
  (t/testing "Succesfully removing gene CGC names."
    (let [fixtures (map (fn [sample]
                          (assoc sample :gene/cgc-name (tu/cgc-name-for-sample sample)))
                        (tu/gen-sample gsg/cloned 2))
          cgc-names (map  :gene/cgc-name fixtures)]
     (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (let [[status body] (retract-gene-cgc-name {:data cgc-names :prov basic-prov})]
            (t/is (ru-hp/ok? {:status status :body body}))
            ;; TODO: check batch with (wng/query-batch db <bid>)
            ))))))

(t/deftest batch-retract-variation-name-success
  (t/testing "Succesfully removing variation names."
    (let [fixtures (gen/sample gsv/payload 2)
          names (map :variation/name fixtures)]
     (tu/with-fixtures
        fixtures
        (fn [conn]
          (let [[status body] (retract-variation-name {:data names :prov basic-prov})]
            (t/is (ru-hp/ok? {:status status :body body}))
            ;; TODO: check batch with (wng/query-batch db <bid>)
            ))))))
