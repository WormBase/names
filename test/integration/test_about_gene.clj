(ns integration.test-about-gene
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.test-utils :as tu]
   [wormbase.db-testing :as db-testing]
   [wormbase.names.service :as service]
   [wormbase.api-test-client :as api-tc]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn- gen-sample []
  (let [[sample] (tu/gene-samples 1)
        gene-id (:gene/id sample)
        prod-seq-name (tu/seq-name-for-sample sample)]
    [gene-id (assoc
              sample
              :gene/id gene-id
              :gene/cgc-name (tu/cgc-name-for-sample sample)
              :gene/sequence-name (tu/seq-name-for-sample sample)
              :gene/status :gene.status/live)]))

(def about-gene (partial api-tc/info "gene"))

(t/deftest test-about
  (t/testing "Info about a live gene can be retrieved by WBGene ID."
    (let [[id data-sample] (gen-sample)]
      (tu/with-gene-fixtures
        data-sample
        (fn check-gene-info [conn]
          (let [[status body] (about-gene id)]
            (tu/status-is? status 200 body)))))))
