(ns integration.test-gene-summary
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [ring.util.http-predicates :as ru-hp]
   [ring.util.http-response :refer [not-found ok]]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.test-utils :as tu]
   [wormbase.db-testing :as db-testing]
   [wormbase.names.service :as service]
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
        (fn check-gene-summary [conn]
          (let [[status body] (summary gene-id)]
            (t/is (ru-hp/ok? {:status status :body body}))))))))

(t/deftest maltformed-identifier
  (t/testing "A malformed identifier results in a 404 response."
    (let [[id data-sample] (gen-sample)]
      (tu/with-fixtures
        data-sample
        (fn check-missing [conn]
          (let [[status body] (summary "WBVar1231231231")]
            (t/is (ru-hp/not-found? {:status status :body body}))))))))
