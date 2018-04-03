(ns integration.test-new-person
  (:require
   [clojure.test :as t]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.api-test-client :as api-tc]
   [org.wormbase.test-utils :as tu]
   [clojure.spec.alpha :as s]
   [org.wormbase.specs.person :as owsp]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def new-person (partial api-tc/new "person"))

(t/deftest must-meet-spec
  (t/testing "Attempting to create new person without required fields yield 400"
    (doseq [payload [{}
                     {:person/email "nobody"}
                     {:person/id "WBPerson123"}]]
      (let [[status body] (new-person payload)]
        (tu/status-is? 400 status body)))))

(t/deftest create
  (t/testing "Valid person data returns a created response (201)"
    (let [sample (first (tu/person-samples 1))
          [status body] (new-person sample)]
      (tu/status-is? 201 status body))))




