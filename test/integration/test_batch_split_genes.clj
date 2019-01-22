(ns integration.test-batch-split-genes
  (:require
   [clj-uuid :as uuid]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as str]
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-response :refer [bad-request conflict not-found ok]]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db :as wdb]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.names.gene :refer [query-batch]]
   [wormbase.test-utils :as tu]
   [wormbase.util :as wu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def elegans-ln "Caenorhabditis elegans")

(defn split-genes [data]
  (api-tc/send-request "batch" :post (assoc data :batch-size 100) :sub-path "gene/split"))

(def basic-prov {:provenance/who {:person/email "tester@wormbase.org"}})

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [[status body] (split-genes {:data [] :prov nil})]
      (t/is (= (:status (bad-request)) status)))))

(t/deftest invalid-db-state
  (t/testing "Batch rejected when one or more genes specified to be merged are dead in db."
    (let [fixtures (tu/gene-samples 1)
          seq-name (tu/seq-name-for-species elegans-ln)
          fixtures** (map (fn [fixture]
                            (assoc fixture
                                   :gene/status :gene.status/dead
                                   :gene/species elegans-ln
                                   :gene/sequence-name (tu/seq-name-for-species elegans-ln)
                                   :gene/biotype (first (gen/sample gsg/biotype 1))))
                          fixtures)
          gene-id (-> fixtures** first :gene/id)
          new-biotype :biotype/transcript
          prod-biotype :biotype/cds
          data [{:from-id gene-id
                 :new-biotype new-biotype
                 :product-biotype prod-biotype
                 :product-sequence-name "SEQPRODUCT1.1"}]

          _ (println "DATA FOR SPLIT:")
          _ (prn data)]
      (tu/with-gene-fixtures
        fixtures** 
        (fn [conn]
          (let [[status body] (split-genes {:data data :prov basic-prov})]
            (tu/status-is? (:status (ok)) status body)))))))

(t/deftest success
  (t/testing "A succesful specification of split operations."
    (let [gene-ids (gen/sample gsg/id 2)
          fixtures (keep-indexed
                    (fn [idx fixture]
                      (assoc fixture
                             :gene/id (nth gene-ids idx)
                             :gene/sequence-name (tu/seq-name-for-species)
                             :gene/status :gene.status/live
                             :gene/biotype :biotype/cds))
                    gene-ids)
          from-seq-name (-> fixtures first :gene/sequence-name)
          bdata [{:from-id (:gene/id (first fixtures))
                  :new-biotype :biotype/psuedogene
                  :product-biotype :biotype/transcript}
                 {:from-id (:gene/id (second fixtures))
                  :new-biotype nil      ;; no change
                  :product-biotype :biotype/pseudogene
                  :product-sequence-name "SEQ2.2"}]]
      (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (let [[status body] (split-genes {:data bdata :prov basic-prov})
                bid (get-in body [:batch/id] "")]
            (tu/status-is? (:status (ok)) status body)
            (t/is (uuid/uuid-string? bid))
            (let [batch-info (query-batch (d/db conn) (uuid/as-uuid bid))
                  batch-lookup (into {}
                                     (map (fn [bi]
                                            [(:gene/sequence-name bi) bi])
                                          batch-info))]
              (doseq [split-spec-data bdata]
                (let [from-gene (get batch-lookup (:from-seq-name split-spec-data))
                      into-gene (get batch-lookup (:into-gene split-spec-data))]
                  (t/is (= (get-in from-gene [:gene/status :db/ident]) :gene.status/dead))
                  (t/is (= (get-in into-gene [:gene/status :db/ident]) :gene.status/live))
                  (t/is (= (get-in into-gene [:gene/biotype :db/ident])
                           (:new-biotype split-spec-data))))))))))))




