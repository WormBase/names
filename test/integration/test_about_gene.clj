(ns integration.test-about-gene
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [org.wormbase.fake-auth :as fake-auth]
   [org.wormbase.test-utils :as tu]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.names.service :as service]
   [org.wormbase.api-test-client :as api-tc]))

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

(defn- check-gene-data []
  (assert false "TBD"))

(t/deftest test-about-live
  (t/testing "Info about a live gene can be retrieved by WBGene ID."
    (let [[id data-sample] (gen-sample)]
      (tu/with-gene-fixtures
        data-sample
        (fn check-gene-info [conn]
          (let [[status body] (about-gene id)]
            (tu/status-is? status 200 body)))))))
