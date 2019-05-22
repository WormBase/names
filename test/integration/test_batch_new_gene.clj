(ns integration.test-batch-new-gene
  (:require
   [clojure.test :as t]
   [clj-uuid :as uuid]
   [datomic.api :as d]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov elegans-ln]]
   [wormbase.db :as wdb]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.gen-specs.species :as gss]
   [wormbase.names.service :as service]
   [wormbase.names.batch :as wnb]
   [wormbase.test-utils :as tu]
   [wormbase.fake-auth]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn new-genes [data]
  (api-tc/send-request "batch" :post data :sub-path "gene"))

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [[status body] (new-genes {:data [] :prov nil})]
      (t/is (ru-hp/bad-request? {:status status :body body})))))

(t/deftest single-item
  (t/testing "Batch with one item accepted, returns batch id."
    (let [bdata [{:gene/sequence-name "AAH1.1"
                  :gene/species elegans-ln
                  :gene/biotype :biotype/cds
                  }]
          [status body] (new-genes {:data bdata :prov basic-prov})]
      (t/is (ru-hp/created? {:status status :body body}))
      (t/is (get body :batch/id "") (pr-str body)))))

(t/deftest non-uniq-names
  (t/testing "Batch with multiple items, unique names is rejected."
    (let [bdata [{:gene/cgc-name "dup-1"
                  :gene/species elegans-ln}
                 {:gene/cgc-name "dup-1"
                  :gene/species elegans-ln}]
          [status body] (new-genes {:data bdata :prov basic-prov})]
      (t/is (ru-hp/conflict? {:status status :body body})))))

(t/deftest genes-invalid-species
  (t/testing "Batch with invalid species is rejected."
    (let [bdata [{:gene/cgc-name "dup-1"
                  :gene/species "Caenorhabditis donkey"}]
          [status body] (new-genes {:data bdata :prov basic-prov})]
      (t/is (ru-hp/bad-request? {:status status :body body})))))

(t/deftest batch-success
  (t/testing "Batch with a random number of items is successful"
    (let [bdata [{:gene/cgc-name "okay-1"
                  :gene/species elegans-ln}
                 {:gene/sequence-name "OKIDOKE.1"
                  :gene/biotype :biotype/cds
                  :gene/species elegans-ln}]
          [status body] (new-genes {:data bdata :prov basic-prov :force false})]
      (t/is (ru-hp/created? {:status status :body body}))
      (let [bid (get body :batch/id "")]
        (t/is (uuid/uuid-string? bid) (pr-str body))
        (let [batch (tu/query-gene-batch (d/db wdb/conn) (uuid/as-uuid bid))
              xs (map #(get-in % [:gene/status :db/ident]) batch)
              [summary-status summary-body] (api-tc/summary "batch" bid)]
          (t/is (seq xs))
          (t/is (every? (partial = :gene.status/live) xs))
          (t/is (ru-hp/ok? {:status summary-status :body summary-body}))
          (t/is (= (some-> summary-body :provenance/what keyword)
                   :event/new-gene)))))))

(t/deftest batch-success-with-force-override-nomenclature
  (t/testing "Batch with a random number of items overriding nomenclature guide is successful"
    (let [bdata [{:gene/cgc-name "FORCETHISBECAUSEIWANTIT"
                  :gene/species elegans-ln}
                 {:gene/sequence-name "OKIDOKE.1"
                  :gene/biotype :biotype/cds
                  :gene/species elegans-ln}]
          [status body] (new-genes {:data bdata :prov basic-prov :force true})]
      (t/is (ru-hp/created? {:status status :body body}))
      (let [bid (get body :batch/id "")]
        (t/is (uuid/uuid-string? bid) (pr-str body))
        (let [batch (tu/query-gene-batch (d/db wdb/conn) (uuid/as-uuid bid))
              xs (map #(get-in % [:gene/status :db/ident]) batch)
              [summary-status summary-body] (api-tc/summary "batch" bid)]
          (t/is (seq xs))
          (t/is (every? (partial = :gene.status/live) xs))
          (t/is (ru-hp/ok? {:status summary-status :body summary-body}))
          (t/is (= (some-> summary-body :provenance/what keyword)
                   :event/new-gene)))))))
