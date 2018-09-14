(ns integration.test-merge-genes
  (:require
   [clojure.test :as t]
   [datomic.api :as d]
   [wormbase.db :as wdb]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.db-testing :as db-testing]
   [wormbase.names.service :as service]
   [wormbase.test-utils :as tu])
  (:import (java.util Date)))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn query-provenance [conn from-gene-id into-gene-id]
  (when-let [mtx (d/q '[:find ?tx .
                        :in $ ?from ?into
                        :where
                        [?tx :provenance/merged-from ?from]
                        [?tx :provenance/merged-into ?into]
                        [?from :gene/status :gene.status/dead]]
                      (-> conn d/db d/history)
                      [:gene/id from-gene-id]
                      [:gene/id into-gene-id])]
    (d/pull (d/db conn)
            '[:provenance/why
              :provenance/when
              {:provenance/merged-into [:gene/id]
               :provenance/merged-from [:gene/id]
               :provenance/how [:db/ident]
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
  [from-id into-id & {:keys [current-user]
                      :or {current-user "tester@wormbase.org"}}]
  (binding [fake-auth/*gapi-verify-token-response*
            (fake-auth/payload {"email" current-user})]
    (let [current-user-token (get fake-auth/tokens current-user)]
      (tu/delete service/app
                 (str "/api/gene/" into-id "/merge/" from-id)
                 "application/json"
                 {"authorization" (str "Token " current-user-token)}))))

(t/deftest must-meet-spec
  (t/testing "Request to merge genes must meet spec."
    (let [response (merge-genes {} "WBGene0000001" "WBGene0000002")
          [status body] response]
      (tu/status-is? status 400 body)))
  (t/testing "Target biotype always required when merging genes."
    (let [[status body] (merge-genes {} "WB000000002" "WBGene00000001")]
      (tu/status-is? status 400 body))))

(t/deftest response-codes
  (t/testing "400 for invalid gene "
    (let [[status body] (merge-genes
                         {:gene/biotype :biotype/transposable-element-gene}
                         "WB2"
                         "WB1")]
      (tu/status-is? 400 status body)))
  (t/testing "404 for missing gene(s)"
    (let [[status body] (merge-genes
                         {:gene/biotype :biotype/transposable-element-gene}
                         "WBGene20000000"
                         "WBGene10000000")]
      (tu/status-is? 404 status body)))
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
          (let [[status body] (merge-genes {:gene/biotype :biotype/cds}
                                           from-id
                                           into-id)]
            (tu/status-is? status 409 body))))))
  (t/testing "400 for validation errors"
    (let [data-samples (tu/gene-samples 2)
          [from-id into-id] (map :gene/id data-samples)]
      (tu/with-gene-fixtures
        data-samples
        (fn check-biotype-validation-error [conn]
          (let [[status body] (merge-genes {:gene/biotype :biotype/godzilla}
                                           from-id
                                           into-id)]
            (tu/status-is? status 400 body)
            (t/is (re-seq #"Invalid.*biotype" (:message body)))))))))

(t/deftest provenance-recorded
  (t/testing "Provenence for successful merge is recorded."
    (let [data-samples (tu/gene-samples 2)
          [gene-from gene-into] data-samples
          [from-id into-id] (map :gene/id data-samples)
          samples [(dissoc gene-from :gene/cgc-name)
                   (-> gene-into
                       (assoc :gene/species (:gene/species gene-from))
                       (dissoc :gene/sequence-name))]]
      (tu/with-gene-fixtures
        samples
        (fn check-provenance [conn]
          (let [db (d/db conn)
                [status body] (merge-genes
                               {:gene/biotype :biotype/transposable-element-gene}
                               from-id
                               into-id)
                prov (query-provenance conn from-id into-id)
                [src tgt] (map #(d/entity db [:gene/id %])
                               [from-id into-id])]
            (tu/status-is? status 200 body)
            (t/is (some-> prov :provenance/when inst?))
            (t/is (= (some-> prov :provenance/merged-into :gene/id)
                     into-id))
            (t/is (= (some-> prov :provenance/merged-from :gene/id)
                     from-id))
            (t/is (= (some-> prov :provenance/who :person/email)
                     "tester@wormbase.org"))
            ;; TODO: this should be dependent on the client used for
            ;;       the request.  at the momment, defaults to
            ;;       web-form.
            (t/is (= (some-> prov :provenance/how :db/ident)
                     :agent/web))))))))

(t/deftest undo-merge
  (t/testing "Undoing a merge operation."
    (let [species :species/c-elegans
          merged-into "WBGene00000001"
          merged-from "WBGene00000002"
          from-seq-name (tu/seq-name-for-species species)
          into-seq-name (tu/seq-name-for-species species)
          from-gene {:db/id from-seq-name
                     :gene/id merged-from
                     :gene/sequence-name from-seq-name
                     :gene/species [:species/id species]
                     :gene/status :gene.status/dead
                     :gene/biotype :biotype/cds}
          into-gene {:db/id into-seq-name
                     :gene/id merged-into
                     :gene/species [:species/id species]
                     :gene/sequence-name into-seq-name
                     :gene/cgc-name (tu/cgc-name-for-species species)
                     :gene/status :gene.status/live
                     :gene/biotype :biotype/transcript}
          init-txes [from-gene
                     into-gene
                     {:db/id "datomic.tx"
                      :provenance/merged-from from-seq-name
                      :provenance/merged-into into-seq-name
                      :provenance/why
                      "a gene that has been merged for testing undo"
                      :provenance/how :agent/console}]
          conn (db-testing/fixture-conn)]
      (with-redefs [wdb/connection (fn get-fixture-conn [] conn)
                    wdb/db (fn get-db [_] (d/db conn))]
        (let [init-tx-res @(d/transact-async conn init-txes)
              tx (d/q '[:find ?tx .
                        :in $ ?from-lur ?into-lur
                        :where
                        [?from-lur :gene/status :gene.status/dead ?tx]
                        [?tx :provenance/merged-from ?from-lur]
                        [?tx :provenance/merged-into ?into-lur]
                        ]
                      (-> init-tx-res :db-after d/history)
                      [:gene/id merged-from]
                      [:gene/id merged-into])
              [status body] (undo-merge-genes merged-from
                                              merged-into)]
          (tu/status-is? status 200 body)
          (t/is (map? body) (pr-str (type body)))
          (t/is (= (:dead body) merged-from) (pr-str body))
          (t/is (= (:live body) merged-into) (pr-str body)))))))
