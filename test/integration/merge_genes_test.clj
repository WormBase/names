(ns integration.merge-genes-test
  (:require
   [clojure.test :as t]
   [datomic.api :as d]
   [java-time :as jt]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.constdata :refer [elegans-ln]]
   [wormbase.db :as wdb]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.db-testing :as db-testing]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.service :as service]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn query-provenance [conn prov-attr lur]
  (when-let [mtx (d/q '[:find ?tx
                        :in $ ?prov-attr ?lur
                        :where
                        [?pa :db/ident ?prov-attr]
                        [?tx ?pa ?lur]]
                      (-> conn d/db d/history)
                      prov-attr
                      lur)]
    (d/pull (d/db conn)
            '[:provenance/why
              :provenance/when
              {:provenance/how [:db/ident]
               :provenance/who [:person/email]}]
            mtx)))

(defn merge-genes
  [payload from-id into-id
   & {:keys [current-user]
      :or {current-user "tester@wormbase.org"}}]

  (binding [fake-auth/*gapi-verify-token-response*
            (fake-auth/payload {"email" current-user})]
    (let [data (tu/->json payload)
          uri (str "/api/gene/" into-id "/merge/" from-id)
          [status body]
          (tu/raw-put-or-post*
           service/app
           uri
           :post
           data
           "application/json"
           {"authorization" "Token IsnotReleventHere"})]
      [status (tu/parse-body body)])))

(defn undo-merge-genes
  [into-id from-id & {:keys [current-user payload]
                      :or {current-user "tester@wormbase.org"
                           payload {:prov nil}}}]
  (binding [fake-auth/*gapi-verify-token-response*
            (fake-auth/payload {"email" current-user})]
    (let [current-user-token (get fake-auth/tokens current-user)]
      (tu/delete service/app
                 (str "/api/gene/" into-id "/merge/" from-id)
                 "application/json"
                 (tu/->json payload)
                 {"authorization" (str "Token " current-user-token)}))))

(t/deftest must-meet-spec
  (t/testing "Request to merge genes must meet spec."
    (let [response (merge-genes {} "WBGene0000001" "WBGene0000002")
          [status body] response]
      (t/is (ru-hp/bad-request? {:status status :body body}))))
  (t/testing "Target biotype always required when merging genes."
    (let [[status body] (merge-genes {} "WB000000002" "WBGene00000001")]
      (t/is (ru-hp/bad-request? {:status status :body body})))))

(t/deftest response-codes
  (t/testing "404 for missing gene(s)"
    (let [[status body] (merge-genes
                         {:data {:biotype "transposable-element-gene"}
                          :prov nil}
                         "WBGene20000000"
                         "WBGene10000000")]
      (t/is (ru-hp/not-found? {:status status :body body}))))
  (t/testing "409 for conflicting state"
    (let [data-samples (tu/gene-samples 2)
          [from-id into-id] (map :gene/id data-samples)
          [sample-1 sample-2] [(-> data-samples
                                   first
                                   (assoc :gene/status :gene.status/dead)
                                   (dissoc :gene/cgc-name))
                               (second data-samples)]
          samples [sample-1 (-> sample-2
                                (assoc :gene/species (:gene/species sample-1))
                                (assoc :gene/status :gene.status/live)
                                (dissoc :gene/sequence-name)
                                (dissoc :gene/biotype))]]
      (tu/with-gene-fixtures
        samples
        (fn check-conflict-when-both-not-live [conn]
          (let [db (d/db conn)
                entid (partial d/entid db)
                entity (partial d/entity db)
                lur [:gene/id into-id]]
            @(d/transact-async conn [[:db.fn/cas
                                      lur
                                      :gene/status
                                      (-> lur entity :gene/status entid)
                                      (entid :gene.status/dead)]]))
          (let [[status body] (merge-genes {:data {:biotype "cds"}
                                            :prov nil}
                                           from-id
                                           into-id)]
            (t/is (ru-hp/conflict? {:status status :body body})))))))
  (t/testing "400 for validation errors"
    (let [data-samples (tu/gene-samples 2)
          [from-id into-id] (map :gene/id data-samples)]
      (tu/with-gene-fixtures
        data-samples
        (fn check-biotype-validation-error [_]
          (let [[status body] (merge-genes {:data {:biotype "godzilla"}
                                            :prov nil}
                                           from-id
                                           into-id)]
            (t/is (ru-hp/not-found? {:status status :body body}))
            (t/is (re-seq #"does not exist" (get body :message "")))))))))

(t/deftest provenance-recorded
  (t/testing "Provenence for successful merge is recorded."
    (let [data-samples (tu/gene-samples 2)
          [gene-from gene-into] data-samples
          [from-id into-id] (map :gene/id data-samples)
          samples [(-> gene-from
                       (dissoc :gene/cgc-name)
                       (assoc :gene/status :gene.status/live))
                   (-> gene-into
                       (assoc :gene/status :gene.status/live
                              :gene/species (:gene/species gene-from))
                       (dissoc :gene/sequence-name))]]
      (tu/with-gene-fixtures
        samples
        (fn check-provenance [conn]
          (let [[status body] (merge-genes
                               {:data {:biotype "transposable-element-gene"}
                                :prov nil}
                               from-id
                               into-id)
                ppe '[*
                      {:provenance/what [:db/ident]
                       :provenance/who [:person/email :person/name :person/id]
                       :provenance/how [:db/ident]}]
                prov (some->> (wnp/query-provenance (d/db conn)
                                                    (d/log conn)
                                                    [:gene/id from-id]
                                                    #{:gene/merges :gene/splits}
                                                    ppe)
                              (filter #(= (:provenance/what %) "merge-genes"))
                              first)]
            (t/is (ru-hp/ok? {:status status :body body}))
            (t/is (jt/zoned-date-time? (:provenance/when prov)) (pr-str prov))

            (let [{src-merges :gene/_merges} (d/pull (d/db conn)
                                                     [{:gene/_merges [[:gene/id]]}]
                                                     [:gene/id from-id])
                  {tgt-merges :gene/merges} (d/pull (d/db conn)
                                                    [{:gene/merges [[:gene/id]]}]
                                                    [:gene/id into-id])]
              (t/is ((set (map :gene/id src-merges)) into-id))
              (t/is (contains? (set (map :gene/id tgt-merges)) from-id))
              (t/is (= (-> prov :provenance/who :person/email) "tester@wormbase.org") (pr-str prov))
              (t/is (= "web" (:provenance/how prov))))))))))

(t/deftest undo-merge
  (t/testing "Undoing a merge operation."
    (let [species elegans-ln
          merged-from "WBGene00000001"
          merged-into "WBGene00000002"
          from-seq-name (tu/seq-name-for-species species)
          into-seq-name (tu/seq-name-for-species species)
          init-from-gene {:db/id "from"
                          :gene/id merged-from
                          :gene/sequence-name from-seq-name
                          :gene/species [:species/latin-name species]
                          :gene/status :gene.status/dead
                          :gene/biotype :biotype/cds}
          from-gene [:db/add [:gene/id merged-from] :gene/merges "into"]
          into-gene {:db/id "into"
                     :gene/id merged-into
                     :gene/species [:species/latin-name species]
                     :gene/sequence-name into-seq-name
                     :gene/cgc-name (tu/cgc-name-for-species species)
                     :gene/status :gene.status/live
                     :gene/biotype :biotype/transcript}
          merge-txes [from-gene
                      into-gene
                      {:db/id "datomic.tx"
                       :provenance/why "a gene that has been merged for testing undo"
                       :provenance/how :agent/console}]
          conn (db-testing/fixture-conn)]
      @(d/transact conn [init-from-gene])
      @(d/transact conn merge-txes)
      (with-redefs [wdb/connection (fn get-fixture-conn [] conn)
                    wdb/connect (fn get-fixture-conn [] conn)
                    wdb/db (fn get-db [_] (d/db conn))]
        (let [[status body] (undo-merge-genes merged-into merged-from)]
          (t/is (ru-hp/ok? {:status status :body body}))
          (t/is (map? body) (pr-str (type body)))
          (t/is (= (:dead body) merged-from) (pr-str body))
          (t/is (= (:live body) merged-into) (pr-str body)))))))
