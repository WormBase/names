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
    (let [response (new-genes {:data [] :prov nil})]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest single-item
  (t/testing "Batch with one item accepted, returns batch id."
    (let [bdata [{:gene/sequence-name "AAH1.1"
                  :gene/species elegans-ln
                  :gene/biotype :biotype/cds
                  }]
          response (new-genes {:data bdata :prov basic-prov})]
      (t/is (ru-hp/created? response))
      (t/is (get-in response [:body :batch/id] "") (pr-str response)))))

(t/deftest non-uniq-names
  (t/testing "Batch with multiple items, unique names is rejected."
    (let [bdata [{:gene/cgc-name "dup-1"
                  :gene/species elegans-ln}
                 {:gene/cgc-name "dup-1"
                  :gene/species elegans-ln}]
          response (new-genes {:data bdata :prov basic-prov})]
      (t/is (ru-hp/conflict? response)))))

(t/deftest genes-invalid-species
  (t/testing "Batch with invalid species is rejected."
    (let [bdata [{:gene/cgc-name "dup-1"
                  :gene/species "Caenorhabditis donkey"}]
          response (new-genes {:data bdata :prov basic-prov})]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest batch-success
  (t/testing "Batch with a random number of items is successful"
    (let [bdata [{:gene/cgc-name "okay-1"
                  :gene/species elegans-ln}
                 {:gene/sequence-name "OKIDOKE.1"
                  :gene/biotype :biotype/cds
                  :gene/species elegans-ln}]
          response (new-genes {:data bdata :prov basic-prov :force false})]
      (t/is (ru-hp/created? response))
      (let [bid (get-in response [:body :batch/id] "")
            ids-created (get-in response [:body :ids])]
        (t/is (every? (juxt :gene/cgc-name :gene/sequence-name :gene/id) ids-created))
        (t/is (uuid/uuid-string? bid) (pr-str response))
        (let [batch (tu/query-gene-batch (d/db wdb/conn) (uuid/as-uuid bid))
              xs (map #(get-in % [:gene/status :db/ident]) batch)
              response2 (api-tc/summary "batch" bid)]
          (t/is (seq xs))
          (t/is (every? (partial = :gene.status/live) xs))
          (t/is (ru-hp/ok? response2))
          (t/is (= (some-> response2 :body :provenance/what keyword)
                   :event/new-gene)))))))

(t/deftest batch-success-with-force-override-nomenclature
  (t/testing "Batch with a random number of items overriding nomenclature guide is successful"
    (let [bdata [{:gene/cgc-name "FORCETHISBECAUSEIWANTIT"
                  :gene/species elegans-ln}
                 {:gene/sequence-name "OKIDOKE.1"
                  :gene/biotype :biotype/cds
                  :gene/species elegans-ln}]
          response (new-genes {:data bdata :prov basic-prov :force true})]
      (t/is (ru-hp/created? response))
      (let [bid (get-in response [:body :batch/id] "")]
        (t/is (uuid/uuid-string? bid) (pr-str response))
        (when bid
          (let [batch (tu/query-gene-batch (d/db wdb/conn) (uuid/as-uuid bid))
                xs (map #(get-in % [:gene/status :db/ident]) batch)
                response (api-tc/summary "batch" bid)]
            (t/is (seq xs))
            (t/is (every? (partial = :gene.status/live) xs))
            (t/is (ru-hp/ok? response))
            (t/is (= (some-> response :body :provenance/what keyword)
                     :event/new-gene))))))))
