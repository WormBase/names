(ns integration.test-about-gene
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [ring.util.http-response :refer [ok]]
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

(def summary (partial api-tc/info "gene"))

(t/deftest test-summary
  (t/testing "Summary of a live gene can be retrieved by WBGene ID."
    (let [[gene-id data-sample] (gen-sample)]
      (tu/with-gene-fixtures
        data-sample
        (fn check-gene-info [conn]
          (let [[status body] (summary gene-id)]
            (tu/status-is? (:status (ok)) status body)))))))
