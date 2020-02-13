(ns integration.batch-new-gene-test
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
    (let [bdata [{:sequence-name "AAH1.1"
                  :species elegans-ln
                  :biotype "cds"}]
          response (new-genes {:data bdata :prov basic-prov})]
      (t/is (ru-hp/created? response))
      (t/is (get-in response [:body :id] "") (pr-str response)))))

(t/deftest non-uniq-names
  (t/testing "Batch with multiple items, unique names is rejected."
    (let [bdata [{:cgc-name "dup-1"
                  :species elegans-ln}
                 {:cgc-name "dup-1"
                  :species elegans-ln}]
          response (new-genes {:data bdata :prov basic-prov})]
      (t/is (ru-hp/conflict? response)))))

(t/deftest genes-invalid-species
  (t/testing "Batch with invalid species is rejected."
    (let [bdata [{:cgc-name "dup-1"
                  :species "Caenorhabditis donkey"}]
          response (new-genes {:data bdata :prov basic-prov})]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest batch-success
  (t/testing "Batch with a random number of items is successful"
    (let [bdata [{:cgc-name "okay-1"
                  :species elegans-ln}
                 {:sequence-name "OKIDOKE.1"
                  :biotype "cds"
                  :species elegans-ln}]
          response (new-genes {:data bdata :prov basic-prov :force false})]
      (t/is (ru-hp/created? response))
      (let [bid (get-in response [:body :id] "")
            ids-created (get-in response [:body :ids])]
        (t/is (every? (juxt :cgc-name :sequence-name :id) ids-created))
        (t/is (= (count ids-created) (count bdata)))
        (t/is (uuid/uuid-string? bid) (pr-str response))
        (let [batch (tu/query-gene-batch (d/db wdb/conn) (uuid/as-uuid bid))
              xs (->> batch
                      (remove :counter/gene)
                      (map #(get-in % [:gene/status :db/ident])))
              response2 (api-tc/summary "batch" bid)]
          (t/is (seq xs))
          (t/is (every? (partial = :gene.status/live) xs))
          (t/is (ru-hp/ok? response2))
          (t/is (= "new-gene"
                   (some-> response2 :body :what))))))))

(t/deftest batch-success-with-force-override-nomenclature
  (t/testing "Batch with a random number of items overriding nomenclature guide is successful"
    (let [bdata [{:cgc-name "FORCETHISBECAUSEIWANTIT"
                  :species elegans-ln}
                 {:sequence-name "OKIDOKE.1"
                  :biotype "cds"
                  :species elegans-ln}]
          response (new-genes {:data bdata :prov basic-prov :force true})]
      (t/is (ru-hp/created? response))
      (let [bid (get-in response [:body :id] "")]
        (t/is (uuid/uuid-string? bid) (pr-str response))
        (when bid
          (let [batch (tu/query-gene-batch (d/db wdb/conn) (uuid/as-uuid bid))
                xs (->> batch
                        (remove :counter/gene)
                        (map #(get-in % [:gene/status :db/ident])))
                response (api-tc/summary "batch" bid)]
            (t/is (seq xs))
            (t/is (every? (partial = :gene.status/live) xs))
            (t/is (ru-hp/ok? response))
            (t/is (= "new-gene" (some-> response :body :what)))))))))
