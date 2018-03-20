(ns integration.test-kill-gene
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [org.wormbase.fake-auth :as fake-auth]
   [org.wormbase.test-utils :as tu]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.names.service :as service]
   [org.wormbase.specs.agent :as owsa]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn- gen-sample-for-kill []
  (let [[[gene-id] _ [_ sample]] (tu/gene-samples 1)
        species (-> sample :gene/species :species/id)
        prod-seq-name (tu/seq-name-for-species species)
        data-sample (assoc sample
                    :gene/id gene-id
                    :gene/sequence-name (tu/seq-name-for-species species)
                    :gene/cgc-name (tu/cgc-name-for-species species)
                    :gene/status :gene.status/live)]
    (prn "SAMPLE FOR KILL pre any modification:" data-sample)
    [gene-id data-sample]))

(defn kill-gene
  [gene-id & {:keys [current-user]
              :or {current-user "tester@wormbase.org"}}]
  (binding [fake-auth/*gapi-verify-token-response* {"email" current-user}]
    (let [current-user-token (get fake-auth/tokens current-user)
          [status body] (tu/delete
                         service/app
                         (str "/gene/" gene-id)
                         "application/edn"
                         {"authorization" (str "Token " current-user-token)})]
      [status (tu/parse-body body)])))

(t/deftest must-meet-spec
  (t/testing "Invalid Gene ID causes a 400 response"
    (let [[status body] (kill-gene "Bill")]
      (t/is (= status 400)))))

(t/deftest gene-must-be-live
  (t/testing "Attempting to kill a dead gene results in a conflict."
    (let [[gene-id sample] (gen-sample-for-kill)
          fixture-data (assoc sample :gene/status :gene.status/dead)]
      (tu/with-fixtures
        fixture-data
        (fn attempt-kill-dead-gene [conn]
          (let [[status body] (kill-gene gene-id)]
            (tu/status-is? status 409 body)))))))

(t/deftest successful-assassination
  (t/testing "Succesfully killing a gene"
    (let [[gene-id sample] (gen-sample-for-kill)]
      (tu/with-fixtures
        sample
        (fn check-dead-after-kill [conn]
          (let [gene-id (:gene/id sample)
                [status body] (kill-gene gene-id)
                db (d/db conn)
                ent (d/entity db [:gene/id gene-id])]
            (t/is (= status 200) (pr-str body))
            (t/is (= (:gene/status ent) :gene.status/dead))))))))

(defn query-provenance [conn gene-id]
  (when-let [tx-ids (d/q '[:find [?tx]
                           :in $ ?lur
                           :where
                           [?lur :gene/status :gene.status/dead ?tx]]
                         (-> conn d/db d/history)
                         [:gene/id gene-id])]
    (map #(d/pull (d/db conn)
                  '[:provenance/why
                    :provenance/when
                    {:provenance/how [:db/ident]
                     :provenance/who [:person/email]}]
                  %)
         tx-ids)))

(t/deftest provenance
  (t/testing "Succesfully killing a gene"
    (let [[gene-id sample] (gen-sample-for-kill)]
      (tu/with-fixtures
        sample
        (fn check-dead-after-kill [conn]
          (let [gene-id (:gene/id sample)
                [status body] (kill-gene gene-id)
                db (d/db conn)
                ent (d/entity db [:gene/id gene-id])
                provs (query-provenance conn gene-id)]
            (t/is (= (count provs) 1))
            (let [prov (first provs)]
              (t/is (= (some-> prov :provenance/how :db/ident)
                       :agent/web))
              (t/is (= (some-> prov :provenance/who :person/email)
                       "tester@wormbase.org")
                    (str "PROV BACK:" (pr-str prov))))
            (t/is (= status 200) (pr-str body))
            (t/is (= (:gene/status ent) :gene.status/dead))))))))

