(ns integration.test-merge-genes
  (:require
   [clojure.test :as t]
   [datomic.api :as d]
   [java-time :as jt]
   [org.wormbase.db :as owdb]
   [org.wormbase.fake-auth :as fake-auth]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.names.service :as service]
   [org.wormbase.names.util :as ownu]
   [org.wormbase.test-utils :refer [raw-put-or-post*
                                    parse-body
                                    status-is?
                                    body-contains?]]
   [org.wormbase.test-utils :as tu])
  (:import (java.util Date)))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn- query-provenance [conn from-gene-id into-gene-id]
  (some->> (d/q '[:find [?tx ?from-g ?into-g ?how ?when ?who ?why ?status]
                  :in $ ?from-gene-id ?into-gene-id
                  :where
                  [?tx :provenance/merged-from ?from-gene-gid]
                  [?tx :provenance/merged-into ?into-gene-id]
                  [(get-else $ ?from-gene-id :gene/id :no-from) ?from-g]
                  [(get-else $ ?into-gene-id :gene/id :no-into) ?into-g]
                  [?into-gid :gene/status ?sid]
                  [?sid :db/ident ?status]
                  [?tx :provenance/why ?why]
                  [?tx :provenance/when ?when]
                  [?tx :provenance/who ?wid]
                  [?wid :user/email ?who]
                  [?tx :provenance/how ?hid]
                  [?hid :agent/id ?how]]
                (-> conn d/db d/history)
                [:gene/id from-gene-id]
                [:gene/id into-gene-id])
           (zipmap [:provenance/tx
                    :provenance/merged-from
                    :provenance/merged-into
                    :provenance/how
                    :provenance/when
                    :provenance/who
                    :provenance/why])))

(defn merge-genes
  [payload from-id into-id
   & {:keys [current-user]
      :or {current-user "tester@wormbase.org"}}]
  (binding [fake-auth/*current-user* current-user]
    (let [data (pr-str payload)
          current-user-token (get fake-auth/tokens current-user)
          uri (str "/gene/" into-id "/merge-from/" from-id)
          [status body]
          (raw-put-or-post*
           service/app
           uri
           :post
           data
           "application/edn"
           {"authorization" (str "Bearer " current-user-token)})]
      [status (parse-body body)])))

(defn undo-merge-genes
  [from-id into-id & {:keys [current-user]
                      :or {current-user "tester@wormbase.org"}}]
  (binding [fake-auth/*current-user* current-user]
    (let [current-user-token (get fake-auth/tokens current-user)]
      (tu/delete service/app
                 (str "/gene/" into-id "/merge-from/" from-id)
                 "application/edn"
                 {"authorization" (str "Bearer " current-user-token)}))))


(t/deftest must-meet-spec
  (t/testing "Request to merge genes must meet spec."
    (let [response (merge-genes {} "WBGene00000002" "WBGene00000001")
          [status body] response]
      (status-is? status 400 body)
      (t/is (contains? (parse-body body) :problems) (pr-str body))))
  (t/testing "Target biotype always required when merging genes."
    (let [[status body] (merge-genes {} "WB2" "WB1")]
      (status-is? status 400 (format "Body: " body)))))

(t/deftest response-codes
  (t/testing "404 for gene missing"
    (let [[status body] (merge-genes {:gene/biotype :biotype/transposon}
                                     "WB2"
                                     "WB1")]
      (t/is (= status 404) (pr-str body))))
  (t/testing "409 for conflicting state"
    (let [[[from-id into-id] _ data-samples] (tu/gene-samples 2)
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
      (tu/with-fixtures
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
            (t/is (= status 409) (pr-str body)))))))
  (t/testing "400 for validation errors"
    (let [[status body] (merge-genes {:gene/biotype :biotype/godzilla}
                                     "WBGene00000002"
                                     "WBGene00000001")]
      (t/is (= status 400) (pr-str body))
      (t/is (re-seq #"Invalid.*biotype" (:message body))))))

(t/deftest provenance-recorded
  (t/testing "Provenence for successful merge is recorded."
    (let [[[from-id into-id] _ [gene-from gene-into]] (tu/gene-samples 2)
          samples [(dissoc gene-from :gene/cgc-name)
                   (-> gene-into
                       (assoc :gene/species (:gene/species gene-from))
                       (dissoc :gene/sequence-name))]]
      (tu/with-fixtures
        samples
        (fn check-provenance [conn]
          (let [db (d/db conn)
                user-email "tester@wormbase.org"
                response (merge-genes {:gene/biotype :biotype/transposon}
                                      from-id
                                      into-id
                                      :current-user user-email)
                prov (query-provenance conn from-id into-id)
                [src tgt] (map #(d/entity db [:gene/id %])
                               [from-id into-id])]
            (t/is (-> prov :provenance/when inst?))
            (t/is (= (:provenance/merged-into prov) into-id))
            (t/is (= (:provenance/merged-from prov) from-id))
            (t/is (= (:provenance/who prov) user-email))
            ;; TODO: this should be dependent on the client used for the request.
            ;;       at the momment, defaults to web-form.
            (t/is (= (:provenance/how prov) :agent/web-form))))))))

(t/deftest undo-merge
  (t/testing "Undoing a merge operation."
    (let [species :species/c-elegans
          merged-into "WBGene00000001"
          merged-from "WBGene00000002"
          from-seq-name (tu/gen-valid-seq-name species)
          into-seq-name (tu/gen-valid-seq-name species)
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
                     :gene/cgc-name (tu/gen-valid-cgc-name species)
                     :gene/status :gene.status/live
                     :gene/biotype :biotype/transcript}
          init-txes [from-gene
                     into-gene
                     {:db/id "datomic.tx"
                      :provenance/merged-from from-seq-name
                      :provenance/merged-into into-seq-name
                      :provenance/why
                      "a gene that has been merged for testing undo"
                      :provenance/how :agent/script}]
          conn (db-testing/fixture-conn)]
      (with-redefs [owdb/connection (fn get-fixture-conn [] conn)
                    owdb/db (fn get-db [_] (d/db conn))]
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
              xxx (println "TX with merge provenence is:" (format "0x%x" tx))
              user-email "tester@wormbase.org"
              [status body] (undo-merge-genes merged-from
                                              merged-into
                                              :current-user user-email)]
          (t/is (= status 200) (pr-str body))
          (t/is (map? body) (type body))
          (t/is (= (:dead body) merged-from) (pr-str body))
          (t/is (= (:live body) merged-into) (pr-str body)))))))
