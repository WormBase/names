(ns integration.batch-retract-gene-cgc-names-test
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
   [wormbase.names.util :as wnu]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn retract-gene-cgc-name [data]
  (let [data* (assoc data :batch-size 1)]
    (api-tc/send-request "batch" :delete data* :sub-path "gene/cgc-name")))

(defn retract-variation-name [data]
  (let [data* (assoc data :batch-size 1)]
    (api-tc/send-request "batch" :delete data* :sub-path "generic/variation/name")))

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (tu/with-installed-generic-entity
      :variation/id
      "WBVar%08d"
      (fn [__]
        (doseq [retract-fn [retract-gene-cgc-name
                            retract-variation-name
                            ]]
          (let [response (retract-fn {:data [] :prov nil})]
            (t/is (ru-hp/bad-request? response))))))))

(defn qualify-ident [kw-ns ident m]
  (if (ident m)
    (wnu/transform-ident-ref ident m kw-ns)
    m))

(t/deftest batch-retract-gene-cgc-name-success
  (t/testing "Succesfully removing gene CGC names."
    (let [fixtures (->> (tu/gen-sample gsg/cloned 2)
                        (map (fn [sample]
                               (assoc sample :cgc-name (tu/cgc-name-for-sample sample))))
                        (map #(wnu/qualify-keys % "gene"))
                        (map (partial qualify-ident "gene.status" :gene/status))
                        (map (partial qualify-ident "biotype" :gene/biotype)))
          cgc-names (map :gene/cgc-name fixtures)]
     (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (let [response (retract-gene-cgc-name {:data cgc-names :prov basic-prov})]
            (t/is (ru-hp/ok? response))
            ;; TODO: check batch with (wng/query-batch db <bid>)
            ))))))

(t/deftest batch-retract-variation-name-success
  (t/testing "Succesfully removing variation names."
    (let [fixtures (->> (gen/sample gsv/payload 2)
                        (map #(wnu/qualify-keys % "variation")))
          names (map :variation/name fixtures)]
      (tu/with-installed-generic-entity
        :variation/id
        "WBVar&08d"
        fixtures
        (fn [conn]
          (let [response (retract-variation-name {:data names :prov basic-prov})]
            (t/is (ru-hp/ok? response))
            ;; TODO: check batch with (wng/query-batch db <bid>)
            ))))))
