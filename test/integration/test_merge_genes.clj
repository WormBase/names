(ns integration.test-merge-genes
  (:require
   [clojure.test :as t]
   [org.wormbase.fake-auth :as fake-auth]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.names.service :as service]
   [org.wormbase.test-utils :refer [raw-put-or-post*
                                    parse-body
                                    status-is?
                                    body-contains?]]
   [org.wormbase.test-utils :as tu]
   [datomic.api :as d]
   [java-time :as jt])
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

(defn gene-merge
  [payload src-id target-id
   & {:keys [current-user]
      :or {current-user "tester@wormbase.org"}}]
  (binding [fake-auth/*current-user* current-user]
    (let [data (pr-str payload)
          current-user-token (get fake-auth/tokens current-user)
          [status body]
          (raw-put-or-post*
           service/app
           (str "/gene/" src-id "/merge/" target-id)
           :post
           data
           "application/edn"
           {"authorization" (str "Bearer " current-user-token)})]
      [status (parse-body body)])))

(t/deftest must-meet-spec
  (t/testing "Request to merge genes must meet spec."
    (let [response (gene-merge {} "WBGene00000001" "WBGene00000002")
          [status body] response]
      (status-is? status 400 body)
      (t/is (contains? (parse-body body) :problems) (pr-str body))))
  (t/testing "Species should always be required when creating gene name."
    (let [[status body] (gene-merge {:gene/cgc-name "abc-1"}
                                    "WB1"
                                    "WB2")]
      (status-is? status 400 (format "Body: " body)))))

(t/deftest response-codes
  (t/testing "404 for gene missing"
    (let [[status body] (gene-merge {:gene/biotype :biotype/transposon}
                                    "WB1"
                                    "WB2")]
      (t/is (= status 404) (pr-str body))))
  (t/testing "409 for conflicting state"
    (let [[[src-id target-id] _ data-samples] (tu/gene-samples 2)
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
                  lur [:gene/id target-id]]
              @(d/transact-async conn [[:db.fn/cas
                                        lur
                                        :gene/status
                                        (-> lur entity :gene/status entid)
                                        (entid :gene.status/dead)]]))
            (let [[status body] (gene-merge {:gene/biotype :biotype/cds}
                                            src-id
                                            target-id)]
              (t/is (= status 409) (pr-str body)))))))
  (t/testing "400 for validation errors"))

(t/deftest provenance-recorded
  (t/testing "Provenence for successful merge is recorded."
    (let [[gene-ids _ [gene-1 gene-2]] (tu/gene-samples 2)
          samples [(dissoc gene-1 :gene/cgc-name)
                   (-> gene-2
                       (assoc :gene/species (:gene/species gene-1))
                       (dissoc :gene/sequence-name))]]
      (tu/with-fixtures
        samples
        (fn check-provenance [conn]
          (let [db (d/db conn)
                user-email "tester@wormbase.org"
                params (-> {:gene/biotype :biotype/transposon}
                           (cons gene-ids)
                           (concat [:current-user user-email]))
                response (apply gene-merge params)
                prov (query-provenance conn
                                       (first gene-ids)
                                       (second gene-ids))
                [src tgt] (map #(d/entity db [:gene/id %]) gene-ids)]
            (t/is (-> prov :provenance/when inst?))
            (t/is (= (:provenance/merged-from prov)
                     (first gene-ids)))
            (t/is (= (:provenance/who prov) user-email))
            ;; TODO: this should be dependent on the client used for the request.
            ;;       at the momment, defaults to web-form.
            (t/is (= (:provenance/how prov) :agent/web-form))))))))
