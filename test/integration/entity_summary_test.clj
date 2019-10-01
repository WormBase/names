(ns integration.entity-summary-test
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.gen-specs.variation :as gsv]
   [wormbase.names.entity :as wne]
   [wormbase.test-utils :as tu]
   [wormbase.db-testing :as db-testing]
   [wormbase.names.service :as service]
   [wormbase.api-test-client :as api-tc]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn- gen-sample []
  (let [id (first (gen/sample gsv/id 1))
        name (first (gen/sample gsv/name 1))
        status :variation.status/live
        sample {:variation/status status
                :variation/name name
                :variation/id id}]
    [id sample]))

(def summary (partial api-tc/summary "entity/variation"))

(t/deftest test-summary
  (t/testing "Summary of a live variation can be retrieved by WBVar ID."
    (let [[id data-sample] (gen-sample)]
      (tu/with-variation-fixtures
        data-sample
        (fn check-variation-summary [conn]
          (let [response (summary id)]
            (t/is (ru-hp/ok? response))))))))

(t/deftest maltformed-identifier
  (t/testing "A malformed identifier results in a 404 response."
    (let [[id data-sample] (gen-sample)]
      (tu/with-variation-fixtures
        data-sample
        (fn check-variation-summary [conn]
          (let [response (summary "WBGene0123123123")]
            (t/is (ru-hp/not-found? response))))))))
