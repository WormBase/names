(ns integration.test-batch-merge-genes
  (:require
   [clj-uuid :as uuid]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as str]
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-response :refer [bad-request conflict not-found ok]]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov]]
   [wormbase.db :as wdb]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.test-utils :as tu]
   [wormbase.util :as wu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn merge-genes [data]
  (api-tc/send-request "batch" :post (assoc data :batch-size 100) :sub-path "gene/merge"))

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [[status body] (merge-genes {:data [] :prov nil})]
      (t/is (= (:status (bad-request)) status)))))

(t/deftest invalid-db-state
  (t/testing "Batch rejected when one or more genes specified to be merged are dead in db."
    (let [fixtures (tu/gene-samples 2)
          fixtures* [(first fixtures)
                     (assoc (second fixtures) :gene/status :gene.status/dead)]
          fixtures** (map (fn [fixture]
                            (assoc fixture
                                   :gene/biotype
                                   (first (gen/sample gsg/biotype 1))))
                          fixtures*)
          gene-ids (map :gene/id fixtures**)
          into-biotype (-> fixtures** second :gene/biotype)
          data [{:from-gene (first gene-ids)
                 :into-gene (second gene-ids)
                 :into-biotype into-biotype}]]
      (tu/with-gene-fixtures
        fixtures**
        (fn [conn]
          (let [[status body] (merge-genes {:data data :prov basic-prov})]
            (tu/status-is? (:status (ok)) status body)))))))

(t/deftest success
  (t/testing "A succesful specification of merge operations."
    (let [gene-ids (gen/sample gsg/id 4)
          uncloned (tu/gen-sample gsg/uncloned 2)
          cloned (tu/gen-sample gsg/cloned 2)
          fixtures (keep-indexed
                    (fn [idx fixture]
                      (assoc fixture
                             :gene/id (nth gene-ids idx)
                             :gene/status :gene.status/live
                             :gene/biotype :biotype/cds))
                    (concat (interleave uncloned cloned)))
          bdata [{:from-gene (:gene/id (first fixtures))
                  :into-gene (:gene/id (nth fixtures 2))
                  :into-biotype :biotype/pseudogene}
                 {:from-gene (:gene/id (second fixtures))
                  :into-gene (:gene/id (nth fixtures 3))
                  :into-biotype :biotype/transcript}]]
      (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (let [[status body] (merge-genes {:data bdata :prov basic-prov})
                bid (get-in body [:batch/id] "")]
            (tu/status-is? (:status (ok)) status body)
            (t/is (uuid/uuid-string? bid))
            (let [batch-info (tu/query-gene-batch (d/db conn) (uuid/as-uuid bid))
                  batch-lookup (into {}
                                     (map (fn [bi]
                                            [(:gene/id bi) bi])
                                          batch-info))]
              (doseq [merge-spec-data bdata]
                (let [from-gene (get batch-lookup (:from-gene merge-spec-data))
                      into-gene (get batch-lookup (:into-gene merge-spec-data))]
                  (t/is (= (get-in from-gene [:gene/status :db/ident]) :gene.status/dead))
                  (t/is (= (get-in into-gene [:gene/status :db/ident]) :gene.status/live))
                  (t/is (= (get-in into-gene [:gene/biotype :db/ident])
                           (:into-biotype merge-spec-data))))))))))))
