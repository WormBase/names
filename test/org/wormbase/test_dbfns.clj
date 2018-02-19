(ns org.wormbase.test-dbfns
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [miner.strgen :as sg]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.db :as owdb]
   [org.wormbase.test-utils :as tu]
   [org.wormbase.specs.gene :as owsg])
  (:import
   (clojure.lang ExceptionInfo)))

(t/use-fixtures :each db-testing/db-lifecycle)

;; TODO: change the generators to generated from either ::owsg/uncloned or ::owsg/cloned rather than ::owsg/update

(defn- gen-valid-seq-name-for-species [species]
  (-> species
      owsg/name-patterns
      :gene/sequence-name
      sg/string-generator
      (gen/sample 1)
      first))

(defn gene-samples [n]
  (assert (int? n))
  (let [gene-refs (->> n
                       (gen/sample (s/gen :gene/id))
                       (map (partial array-map :gene/id)))
        gene-recs (gen/sample (s/gen ::owsg/update) n)
        data-samples (->> (interleave gene-refs gene-recs)
                          (partition n)
                          (map (partial apply merge)))
        gene-ids (map :gene/id (flatten gene-refs))]
    (if-let [dup-seq-names? (->> data-samples
                                 (map :gene/sequence-name)
                                 (reduce =))]
      (recur n)
      [gene-ids gene-recs data-samples])))

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
    (t/testing (str "When merging cloned to uncloned, "
                    "sequence name is transfered: "
                    "eaten gene's sequence name should be retracted.")
      (let [[[src-id target-id]
             gene-recs
             [cloned uncloned]] (gene-samples 2)
            data-samples [(dissoc cloned :gene/cgc-name)
                          (-> uncloned
                              (dissoc :gene/sequence-name)
                              (dissoc :gene/biotype)
                              (assoc :gene/species
                                     (:gene/species cloned)))]]
       (tu/with-fixtures
         data-samples
         (fn seq-name-txferred-to-uncloned [conn]
           (let [db (d/db conn)
                 src-seq-name (:gene/sequence-name
                               (d/entity db [:gene/id src-id]))
                 txes (merge-genes db
                                   src-id
                                   target-id
                                   :gene/id
                                   :biotype/transcript)
                 tx-result (d/with db txes)
                 [src tgt] (map #(d/entity (:db-after tx-result)
                                           [:gene/id %])
                                [src-id target-id])]
             (t/is (= (:gene/sequence-name tgt) src-seq-name)
                   (str "Sequence name not transferred?"
                        (pr-str (d/touch tgt)))))))))
    (t/testing (str "When merging one cloned to another cloned gene, "
                    "sequence name is *not* transfered"
                    "Eaten gene's sequence name is left intact.")
            (let [[[src-id target-id]
                   gene-recs
                   [cloned-1 cloned-2]] (gene-samples 2)
                  tgt-seq-name (:gene/sequence-name cloned-2)
                  data-samples [(dissoc cloned-1 :gene/cgc-name)
                                (-> cloned-2
                                    (dissoc :gene/cgc-name)
                                    (assoc :gene/species
                                           (:gene/species cloned-1)))]]
       (tu/with-fixtures
         data-samples
         (fn seq-name-not-txferred-to-target [conn]
           (let [db (d/db conn)
                 seq-name (:gene/sequence-name
                           (d/entity db [:gene/id src-id]))
                 txes (merge-genes db
                                   src-id
                                   target-id
                                   :gene/id
                                   :biotype/transcript)
                 tx-result (d/with db txes)
                 [src tgt] (map #(d/entity (:db-after tx-result)
                                           [:gene/id %])
                                [src-id target-id])]
             (t/is (= (:gene/sequence-name tgt) tgt-seq-name)
                   (str "Target sequence name should be preserved"
                        (pr-str (d/touch tgt)))))))))
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


(t/deftest test-split-genes
  (let [db (d/db owdb/conn)
        split-gene (partial d/invoke db :wormbase.tx-fns/split-gene)]
    (t/testing "biotype of gene and new one must be supplied"
      (t/is (thrown-with-msg?
             ExceptionInfo
             #"Invalid.*split.*"
             (split-gene db
                         "WBGene00000001"
                         {:product
                          {:gene/sequence-name "ABC.1"
                           :gene/biotype "transcript"}}
                         ::owsg/split))))
    (t/testing "product must be specified"
      (t/is (thrown-with-msg?
             ExceptionInfo
             #"Invalid.*split.*"
             (split-gene db
                         "WBGene00000001"
                         {:gene/biotype :biotype/transcript}
                         ::owsg/split))))
    (t/testing "product sequence-name must be supplied"
      (t/is (thrown-with-msg?
             ExceptionInfo
             #"Invalid.*split.*"
             (split-gene db
                         "WBGene00000001"
                         {:gene/biotype :biotype/cds
                          :product
                          {:gene/biotype :biotype/transposon}}
                         ::owsg/split))))
    (t/testing "product biotype must be supplied"
      (t/is (thrown-with-msg?
             ExceptionInfo
             #"Invalid.*split.*"
             (split-gene db
                         "WBGene00000001"
                         {:gene/biotype :biotype/transposon
                          :product
                          {:gene/sequence-name "ABC.1"}}
                         ::owsg/split))))
    (t/testing "a valid split operation"
      (let [[[gene-id] _ [_ sample]] (gene-samples 1)
            p-seq-name (-> sample
                           :gene/species
                           :species/id
                           gen-valid-seq-name-for-species)
            bt (-> :gene/biotype s/gen (gen/sample 1) first)
            data (assoc {:gene/biotype bt}
                        :product {:gene/sequence-name p-seq-name
                                  :gene/biotype :biotype/cds})
            data-sample (assoc sample :gene/biotype bt :gene/id gene-id)]
        (tu/with-fixtures
          data-sample
          (fn split-ok [conn]
            (let [db (d/db conn)
                  xxx (assert db "DB was nil")
                  txes (split-gene db gene-id data ::owsg/split)
                  tx-result @(d/transact conn txes)
                  db (:db-after tx-result)
                  src-gene (d/entity db [:gene/id gene-id])
                  new-gene (d/entity db [:gene/sequence-name p-seq-name])]
              (t/is (= (:gene/status new-gene) :gene.status/live))
              (t/is (= (:gene/biotype new-gene) (-> data :product :gene/biotype)))
              (t/is (= (:gene/biotype src-gene) (:gene/biotype data-sample))))))))))


(t/deftest test-latest-id-number
  (let [latest-id-number (fn [db ident]
                           (d/invoke db
                                     :wormbase.tx-fns/latest-id-number
                                     db
                                     ident))]
    (t/testing "Getting an id number for non-existant ident"
      (t/is (thrown-with-msg? ExceptionInfo
                              #"Invalid ident"
                              (latest-id-number (d/db owdb/conn) :galaxy/id))))
    (t/testing "Getting an id number when no other data exists"
      (tu/with-fixtures
        []
        (fn no-gnmes [conn]
          (t/is (zero? (latest-id-number (d/db conn) :gene/id))))))
    (t/testing "When we have data"
      (let [[[ids] _ samples] (gene-samples 2)]
        (tu/with-fixtures
          samples
          (fn latest-id-with-data [conn]
            (let [latest-db (d/db conn)
                  latest-id (d/invoke latest-db
                                      :wormbase.tx-fns/latest-id
                                      latest-db
                                      :gene/id)
                  lidn (d/invoke latest-db
                                      :wormbase.tx-fns/latest-id-number
                                      latest-db
                                      :gene/id)]
              (t/is (= (format "WBGene%08d" lidn)
                       (-> samples last :gene/id))))))))))
