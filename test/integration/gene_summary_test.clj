(ns integration.gene-summary-test
  (:require
   [clojure.test :as t]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.test-utils :as tu]
   [wormbase.db-testing :as db-testing]
   [wormbase.api-test-client :as api-tc]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn- gen-sample []
  (let [[sample] (tu/gene-samples 1)
        gene-id (:gene/id sample)]
    [gene-id (assoc
              sample
              :gene/id gene-id
              :gene/status :gene.status/live)]))

(def summary (partial api-tc/summary "gene"))

(t/deftest test-summary
  (t/testing "Summary of a live gene can be retrieved by WBGene ID."
    (let [[gene-id data-sample] (gen-sample)]
      (tu/with-gene-fixtures
        data-sample
        (fn check-gene-summary [_]
          (let [response (summary gene-id)]
            (t/is (ru-hp/ok? response))))))))

(t/deftest maltformed-identifier
  (t/testing "A malformed identifier results in a 404 response."
    (let [[_ data-sample] (gen-sample)]
      (tu/with-fixtures
        data-sample
        (fn check-missing [_]
          (let [response (summary "WBVar1231231231")]
            (t/is (ru-hp/not-found? response))))))))
