(ns wormbase.test-dbfns
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as str]
   [clojure.test :as t]
   [datomic.api :as d]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.gene :as gs]
   [wormbase.db :as wdb]
   [wormbase.test-utils :as tu]
   [wormbase.specs.gene :as wsg])
  (:import
   (clojure.lang ExceptionInfo)))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn gene-samples [n status]
  (->> n (tu/gene-samples) (map #(assoc % :gene/status status))))

(t/deftest test-merge-genes
  (let [db (d/db wdb/conn)
        merge-genes (partial d/invoke db :wormbase.tx-fns/merge-genes)]
    (t/testing "Both merge participants must exist in DB"
      (let [data-samples (gene-samples 2 :gene.status/live)
            [from-id into-id] (map :gene/id data-samples)]
        (t/is
         (thrown-with-msg? ExceptionInfo
                           #"Merge participant does not exist"
                           (merge-genes db
                                        from-id
                                        into-id
                                        :biotype/transposable-element-gene)))))
    (t/testing "Both source and target of merge should be live"
      (let [data-samples (tu/gene-samples 2)
            [from-id into-id] (map :gene/id data-samples)
            [sample-1 sample-2] [(-> data-samples
                                     first
                                     (assoc :gene/status
                                            :gene.status/dead)
                                     (dissoc :gene/cgc-name))
                                 (second data-samples)]
            samples [sample-1 (-> sample-2
                                  (assoc :gene/species
                                         (:gene/species sample-1))
                                  (dissoc :gene/sequence-name)
                                  (dissoc :gene/biotype))]]
        (tu/with-gene-fixtures
          samples
          (fn check-participants-both-live [conn]
            (t/is (thrown-with-msg?
                   ExceptionInfo
                   #".*must be live.*"
                   (merge-genes (d/db conn)
                                from-id
                                into-id
                                :biotype/transposable-element-gene)))))))
    (t/testing (str "When merging cloned to uncloned, "
                    "sequence name is transfered: "
                    "eaten gene's sequence name should be retracted.")
      (let [[cloned uncloned] (gene-samples 2 :gene.status/live)
            [from-id into-id] (map :gene/id [cloned uncloned])
            data-samples [(-> cloned
                              (dissoc :gene/cgc-name)
                              (assoc :gene/status :gene.status/live))
                          (-> uncloned
                              (dissoc :gene/sequence-name)
                              (dissoc :gene/biotype)
                              (assoc :gene/species (:gene/species cloned))
                              (assoc :gene/status :gene.status/live))]]
       (tu/with-gene-fixtures
         data-samples
         (fn seq-name-txferred-to-uncloned [conn]
           (let [db (d/db conn)
                 src-seq-name (:gene/sequence-name
                               (d/entity db [:gene/id from-id]))
                 txes (merge-genes db from-id into-id :biotype/transcript)
                 tx-result (d/with db txes)
                 [src tgt] (map #(d/entity (:db-after tx-result)
                                           [:gene/id %])
                                [from-id into-id])]
             (t/is (= (:gene/sequence-name tgt) src-seq-name)
                   (str "Sequence name not transferred?"
                        (pr-str (d/touch tgt)))))))))
    (t/testing (str "When merging one cloned to another cloned gene, "
                    "sequence name is *not* transfered"
                    "Eaten gene's sequence name is left intact.")
            (let [[cloned-1 cloned-2] (gene-samples 2 :gene.status/live)
                  [from-id into-id] (map :gene/id [cloned-1 cloned-2])
                  tgt-seq-name (:gene/sequence-name cloned-2)
                  data-samples [(dissoc cloned-1 :gene/cgc-name)
                                (-> cloned-2
                                    (dissoc :gene/cgc-name)
                                    (assoc :gene/species
                                           (:gene/species cloned-1)))]]
       (tu/with-gene-fixtures
         data-samples
         (fn seq-name-not-txferred-to-target [conn]
           (let [db (d/db conn)
                 seq-name (:gene/sequence-name
                           (d/entity db [:gene/id from-id]))
                 txes (merge-genes db from-id into-id :biotype/transcript)
                 tx-result (d/with db txes)
                 [src tgt] (map #(d/entity (:db-after tx-result)
                                           [:gene/id %])
                                [from-id into-id])]
             (t/is (= (:gene/sequence-name tgt) tgt-seq-name)
                   (str "Target sequence name should be preserved"
                        (pr-str (d/touch tgt)))))))))
    (t/testing "Valid merge request results in correct TX form"
      (let [data-samples (gene-samples 2 :gene.status/live)
            [from-id into-id] (map :gene/id data-samples)
            [sample-1 sample-2] [(-> data-samples
                                     first
                                     (dissoc :gene/cgc-name)
                                     (assoc :gene/status
                                            :gene.status/live))
                                 (second data-samples)]
            samples [sample-1
                     (assoc sample-2
                            :gene/species
                            (:gene/species sample-1))]]
        (tu/with-gene-fixtures
          samples
          (fn check-tx-forms [conn]
            (let [txes (merge-genes (d/db conn)
                                    from-id
                                    into-id
                                    :biotype/transposable-element-gene)
                  tx-res (d/with (d/db conn) txes)]
              (t/is (map? tx-res))
              (t/is (:db-after tx-res)))))))))

(defn- gen-prod-seq-name [sample]
  (let [sn (-> sample
               :gene/species
               :species/id
               tu/seq-name-for-species)]
    (if (= sn (:gene/sequence-name sample))
      (recur sample)
      sn)))

(t/deftest test-split-genes
  (let [db (d/db wdb/conn)
        split-gene (partial d/invoke db :wormbase.tx-fns/split-gene)]
    (t/testing "a valid split operation"
      (let [[sample] (gene-samples 1 :gene.status/live)
            gene-id (:gene/id sample)
            p-seq-name (gen-prod-seq-name sample)
            bt (first (gen/sample gs/biotype 1))
            data (assoc {:gene/biotype bt}
                        :product {:gene/sequence-name p-seq-name
                                  :gene/biotype :biotype/cds})
            data-sample (assoc sample :gene/biotype bt :gene/id gene-id)]
        (tu/with-gene-fixtures
          data-sample
          (fn split-ok [conn]
            (let [db (d/db conn)
                  txes (split-gene db gene-id data true)
                  tx-result @(d/transact conn txes)
                  db (:db-after tx-result)
                  src-gene (d/entity db [:gene/id gene-id])
                  new-gene (d/entity db [:gene/sequence-name p-seq-name])]
              (t/is (= (:gene/status new-gene) :gene.status/live))
              (t/is (= (:gene/biotype new-gene)
                       (-> data :product :gene/biotype)))
              (t/is (= (:gene/biotype src-gene)
                       (:gene/biotype data-sample))))))))))

(t/deftest test-latest-id-number
  (let [latest-id-number (fn [db ident]
                           (d/invoke db
                                     :wormbase.tx-fns/latest-id-number
                                     db
                                     ident))]
    (t/testing "Getting an id number when no other data exists"
      (tu/with-gene-fixtures
        []
        (fn no-gnmes [conn]
          (t/is (zero? (latest-id-number (d/db conn) :gene/id))))))
    (t/testing "Getting an id number when db populated"
      (let [samples (gene-samples 2 :gene.status/live)
            data-samples (keep-indexed
                          (fn [idx sample]
                            (assoc sample
                                   :gene/id
                                   (format "WBGene%08d" (inc idx))))
                          samples)]
        (tu/with-gene-fixtures
          data-samples
          (fn latest-id-with-data [conn]
            (let [db (d/db conn)
                  invoke (partial d/invoke db)
                  latest-id (invoke :wormbase.tx-fns/latest-id
                                    db
                                    :gene/id)
                  latest-ent (d/entity db [:gene/id latest-id])
                  lidn (invoke :wormbase.tx-fns/latest-id-number
                               db
                               :gene/id)]
              (t/is (str/ends-with? latest-id (str lidn)))
              (t/is (= (format "WBGene%08d" lidn)
                       (-> data-samples last :gene/id)))
              (t/testing "Next Id after kill should take account of dead genes"
                @(d/transact conn [[:db.fn/cas
                                    [:gene/id latest-id]
                                    :gene/status
                                    (d/entid db (:gene/status latest-ent))
                                    (d/entid db :gene.status/dead)]])
                (let [db (d/db conn)
                      invoke (partial d/invoke db)
                      template "WBGene%08d"
                      next-id (invoke :wormbase.tx-fns/next-identifier
                                      db
                                      :gene/id
                                      template)]
                  (t/is (= next-id
                           (format "WBGene%08d" (inc lidn)))))))))))))
