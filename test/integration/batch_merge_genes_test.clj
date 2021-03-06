(ns integration.batch-merge-genes-test
  (:require
   [clj-uuid :as uuid]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov]]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.names.util :as wnu]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn merge-genes [data]
  (api-tc/send-request "batch" :post (assoc data :batch-size 100) :sub-path "gene/merge"))

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [response (merge-genes {:data [] :prov nil})]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest invalid-db-state
  (t/testing "Batch rejected when one or more genes specified to be merged are dead in db."
    (let [fixtures (tu/gene-samples 2)
          fixtures* [(first fixtures)
                     (assoc (second fixtures) :gene/status :gene.status/dead)]
          fixtures** (map (fn [fixture]
                            (assoc fixture
                                   :gene/biotype
                                   (->> (gen/sample gsg/biotype 1)
                                        (first)
                                        (keyword "biotype"))))
                          fixtures*)
          gene-ids (map :gene/id fixtures**)
          into-biotype (-> fixtures** second :gene/biotype)
          data [{:from-gene (first gene-ids)
                 :into-gene (second gene-ids)
                 :into-biotype (name into-biotype)}]]
      (tu/with-gene-fixtures
        fixtures**
        (fn [_]
          (let [response (merge-genes {:data (map #(wnu/unqualify-keys % "gene") data)
                                       :prov basic-prov})]
            (t/is (ru-hp/ok? response))))))))

(t/deftest success
  (t/testing "A succesful specification of merge operations."
    (let [gene-ids (gen/sample gsg/id 4)
          uncloned (map #(wnu/qualify-keys % "gene") 
                        (tu/gen-sample gsg/uncloned 2))
          cloned (map #(wnu/qualify-keys % "gene")
                      (tu/gen-sample gsg/cloned 2))
          fixtures (keep-indexed
                    (fn [idx fixture]
                      (assoc fixture
                             :gene/id (nth gene-ids idx)
                             :gene/status :gene.status/live
                             :gene/biotype :biotype/cds))
                    (concat (interleave uncloned cloned)))
          bdata [{:from-gene (:gene/id (first fixtures))
                  :into-gene (:gene/id (nth fixtures 2))
                  :into-biotype "pseudogene"}
                 {:from-gene (:gene/id (second fixtures))
                  :into-gene (:gene/id (nth fixtures 3))
                  :into-biotype "transcript"}]]
      (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (let [response (merge-genes {:data bdata :prov basic-prov})
                bid (get-in response [:body :id] "")]
            (t/is (ru-hp/ok? response))
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
                  (t/is (= (name (get-in into-gene [:gene/biotype :db/ident]))
                           (:into-biotype merge-spec-data))))))))))))
