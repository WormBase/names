(ns integration.test-batch-retract-gene-cgc-names
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.test :as t]
   [clj-uuid :as uuid]
   [ring.util.http-response :refer [bad-request conflict not-found ok]]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def elegans-ln "Caenorhabditis elegans")

(def basic-prov {:provenance/who {:person/email "tester@wormbase.org"}})

(defn retract [data]
  (let [data* (assoc data :batch-size 1)]
    (api-tc/send-request "batch" :delete data* :sub-path "gene/cgc-name")))

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [[status body] (retract {:data [] :prov nil})]
      (t/is (= (:status (bad-request)) status)))))

(t/deftest batch-success
  (t/testing "Succesfully removing CGC names from genes."
    (let [fixtures (map (fn [sample]
                          (assoc sample :gene/cgc-name (tu/cgc-name-for-sample sample)))
                        (tu/gen-sample gsg/cloned 2))
          cgc-names (map  :gene/cgc-name fixtures)] 
     (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (let [[status body] (retract {:data cgc-names :prov basic-prov})]
            (tu/status-is? (:status (ok)) status body)))))))
