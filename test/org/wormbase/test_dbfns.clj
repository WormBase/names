(ns org.wormbase.test-dbfns
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [org.wormbase.db-testing :as db-testing]

   [org.wormbase.db :as owdb]
   [org.wormbase.test-utils :as tu]
   [org.wormbase.specs.gene :as owsg])
  (:import
   (clojure.lang ExceptionInfo)))

(t/use-fixtures :each db-testing/db-lifecycle)

;; TODO: change the generators to generate from either ::owsg/uncloned or ::owsg/cloned rather than ::owsg/update

(defn gene-samples [n]
  (assert (int? n))
  (let [gene-refs (->> (gen/sample (s/gen :gene/id) n)
                       (map (partial array-map :gene/id)))
        gene-recs (gen/sample (s/gen ::owsg/update) n)
        data-samples (->> (interleave gene-refs gene-recs)
                          (partition n)
                          (map (partial apply merge)))
        gene-ids (map :gene/id (flatten gene-refs))]
    (let [dup-seq-names? (reduce = (map :gene/sequence-name data-samples))]
      (if dup-seq-names?
        (recur n)
        [gene-ids gene-recs data-samples]))))

(t/deftest test-merge-genes
  (let [db (d/db owdb/conn)
        merge-genes (partial d/invoke db :wormbase.tx-fns/merge-genes)]
    (t/testing "Cannot merge two genes with the same identifier."
      (let [[[src-id _] gene-recs data-samples] (gene-samples 2)
            data-sample (first data-samples)]
        (t/is (thrown-with-msg? ExceptionInfo
                                #"cannot.*(same|identical)"
                                (merge-genes db
                                             src-id
                                             src-id
                                             :biotype/transposon
                                             :gene/id)))))
    (t/testing "Target biotype must be supplied"
      (t/is (thrown-with-msg? ExceptionInfo
                              #"Invalid.*biotype"
                              (merge-genes db
                                           "WBGene00000001"
                                           "WBGene00000002"
                                           :gene/id
                                           nil))))
    (t/testing "Gene to be eaten cannot have a GGC name"
      (let [[[src-id target-id] gene-recs samples] (gene-samples 2)
            [sample-1 sample-2] samples
            data-samples [sample-1
                          (assoc sample-2
                                 :gene/species
                                 (:gene/species sample-1))]]
        (tu/with-fixtures
          data-samples
          (fn check-cgc-name-not-eaten [conn]
            (t/is (thrown-with-msg?
                   ExceptionInfo
                   #"killed.*CGC.*refusing to merge"
                   (merge-genes (d/db conn)
                                src-id
                                target-id
                                :gene/id
                                :biotype/transposon)))))))
    (t/testing "Both merge participants must have the same species"
      (let [[[src-id target-id] _ samples] (gene-samples 2)
            [sample-1 sample-2] samples
            data-samples [(-> sample-1
                              (assoc :gene/species
                                     {:species/id :species/c-elegans})
                              (dissoc :gene/cgc-name))
                          (assoc sample-2
                                 :gene/species
                                 {:species/id :species/c-briggsae})]]
        (tu/with-fixtures
          data-samples
          (fn must-have-same-species [conn]
            (let [db (d/db conn)]
              (t/is
               (thrown-with-msg? ExceptionInfo
                                 #"Refusing to merge.*diff.*species"
                                 (merge-genes db
                                              src-id
                                              target-id
                                              :gene/id
                                              :biotype/transposon))))))))
    (t/testing "Both merge participants must exist in DB"
      (let [[[src-id target-id] _ data-samples] (gene-samples 2)]
        (t/is
         (thrown-with-msg? ExceptionInfo
                           #"Merge participant does not exist"
                           (merge-genes db
                                        src-id
                                        target-id
                                        :gene/id
                                        :biotype/transposon)))))
    (t/testing "Both source and target of merge should be live"
      (let [[[src-id target-id] _ data-samples] (gene-samples 2)
            [sample-1 sample-2] [(-> data-samples
                                     first
                                     (assoc :gene/status :gene.status/dead)
                                     (dissoc :gene/cgc-name))
                                 (second data-samples)]
            samples [sample-1 (-> sample-2
                                  (assoc :gene/species (:gene/species sample-1))
                                  (dissoc :gene/sequence-name)
                                  (dissoc :gene/biotype))]]
        (tu/with-fixtures
          samples
          (fn check-participants-both-live [conn]
            (let [txes (merge-genes (d/db conn)
                                    src-id
                                    target-id
                                    :gene/id
                                    :biotype/transposon)
                  tx-res (d/with (d/db conn) txes)]
              (t/is (map? tx-res))
              (t/is (:db-after tx-res)))))))
    (t/testing (str "When merging cloned to uncloned, sequence name is transfered: "
                    "eaten gene's sequence name should be retracted.")
      (let [[[src-id target-id] gene-recs [cloned uncloned]] (gene-samples 2)
            data-samples [(-> cloned
                              (dissoc :gene/cgc-name))
                          (-> uncloned
                              (dissoc :gene/sequence-name)
                              (dissoc :gene/biotype)
                              (assoc :gene/species (:gene/species cloned)))]]
       (tu/with-fixtures
         data-samples
         (fn seq-name-txferred-to-uncloned [conn]
           (let [db (d/db conn)
                 src-seq-name (:gene/sequence-name (d/entity db [:gene/id src-id]))
                 txes (merge-genes db src-id target-id :gene/id :biotype/transcript)
                 tx-result (d/with db txes)
                 [src tgt] (map #(d/entity (:db-after tx-result) [:gene/id %])
                                [src-id target-id])]
             (t/is (:gene/sequence-name tgt) (str "Sequence name not transferred?"
                                                  (pr-str (d/touch tgt)))))))))
                    
    ;; (t/testing (str "When merging cloned to cloned, sequence name is *not* transfered"
    ;;                 "Eaten gene's sequence name is left intact.")
    ;;   (t/is false "TBD"))
    (t/testing "Valid merge request results in correct TX form"
      (let [[[src-id target-id] _ data-samples] (gene-samples 2)
            [sample-1 sample-2] [(-> data-samples
                                     first
                                     (dissoc :gene/cgc-name)
                                     (assoc :gene/status
                                            :gene.status/live))
                                 (second data-samples)]
            samples [sample-1
                     (assoc sample-2 :gene/species (:gene/species sample-1))]]
        (tu/with-fixtures
          samples
          (fn check-tx-forms [conn]
            (let [txes (merge-genes (d/db conn)
                                    src-id
                                    target-id
                                    :gene/id
                                    :biotype/transposon)
                  tx-res (d/with (d/db conn) txes)]
              (t/is (map? tx-res))
              (t/is (:db-after tx-res)))))))))
