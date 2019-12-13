(ns integration.batch-split-genes-test
  (:require
   [clj-uuid :as uuid]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as str]
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-predicates :as ru-hp]   
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov elegans-ln]]
   [wormbase.db :as wdb]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.names.gene :as wng]
   [wormbase.names.util :as wnu]
   [wormbase.test-utils :as tu]
   [wormbase.util :as wu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn split-genes [data]
  (api-tc/send-request "batch" :post (assoc data :batch-size 100) :sub-path "gene/split"))

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [response (split-genes {:data [] :prov nil})]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest invalid-db-state
  (t/testing "Batch rejected when one or more genes specified to be merged are dead in db."
    (let [fixtures (tu/gene-samples 1)
          seq-name (tu/seq-name-for-species elegans-ln)
          fixtures** (map (fn [fixture]
                            (assoc fixture
                                   :gene/status :gene.status/dead
                                   :gene/species elegans-ln
                                   :gene/sequence-name (tu/seq-name-for-species elegans-ln)
                                   :gene/biotype (->> (gen/sample gsg/biotype 1)
                                                      (first)
                                                      (keyword "biotype"))))
                          fixtures)
          gene-id (-> fixtures** first :gene/id)
          new-biotype "transcript"
          prod-biotype "cds"
          data [{:from-id gene-id
                 :new-biotype new-biotype
                 :product-biotype prod-biotype
                 :product-sequence-name "SEQPRODUCT1.1"}]]
      (tu/with-gene-fixtures
        fixtures**
        (fn [conn]
          (let [response (split-genes {:data data :prov basic-prov})]
            (t/is (ru-hp/ok? response))))))))

(t/deftest success
  (t/testing "A succesful specification of split operations."
    (let [gene-ids (gen/sample gsg/id 2)
          fixtures (map
                    (fn [gene-id]
                      {:gene/id gene-id
                       :gene/species [:species/latin-name elegans-ln]
                       :gene/sequence-name (tu/seq-name-for-species elegans-ln)
                       :gene/status :gene.status/live
                       :gene/biotype :biotype/cds})
                     gene-ids)
          from-seq-name (-> fixtures first :gene/sequence-name)
          bdata [{:from-id (:gene/id (first fixtures))
                  :new-biotype "pseudogene"
                  :product-biotype "transcript"
                  :product-sequence-name "SEQ1.1"}
                 {:from-id (:gene/id (second fixtures))
                  :new-biotype nil      ;; no change
                  :product-biotype "pseudogene"
                  :product-sequence-name "SEQ2.2"}]]
      (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (let [response (split-genes {:data bdata :prov basic-prov})
                bid (get-in response [:body :id] "")]
            (t/is (ru-hp/ok? response))
            (t/is (uuid/uuid? bid))
            (let [batch-info (tu/query-gene-batch (d/db conn) (uuid/as-uuid bid))
                  batch-lookup (into {}
                                     (map (fn [bi]
                                            [(:gene/sequence-name bi) bi])
                                          batch-info))]
              (doseq [split-spec-data bdata]
                (let [pull-pattern '[* {:gene/species [:species/latin-name]
                                                         :gene/status [:db/ident]
                                                         :gene/biotype [:db/ident]
                                        :gene/_splits [:gene/id]
                                        :gene/splits [:gene/id]}]
                      pull (partial d/pull (d/db conn) pull-pattern)
                      from-gene (pull [:gene/id (:from-id split-spec-data)])
                      product (pull [:gene/sequence-name (:product-sequence-name split-spec-data)])
                      expected-prod-bt (:product-biotype split-spec-data)
                      expected-new-bt (or (:new-biotype split-spec-data)
                                          (get-in from-gene [:gene/biotype :db/ident]))]
                  (t/is (= (get-in from-gene [:gene/status :db/ident]) :gene.status/live))
                  (t/is (some (fn [g]
                                (= (:gene/id g) (:from-id split-spec-data)))
                              (:gene/_splits product)))
                  (t/is (= (name (get-in product [:gene/status :db/ident])) "live"))
                  (t/is (= (name (get-in product [:gene/biotype :db/ident])) expected-prod-bt)
                        (str "PRODUCT:\n"
                             (pr-str product))))))))))))
