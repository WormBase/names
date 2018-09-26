(ns integration.test-change-status
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-response :refer [ok precondition-failed]]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.names.service :as service]
   [wormbase.specs.agent :as wsa]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn- gen-sample [& {:keys [current-status]
                      :or {current-status :gene.status/live}}]
  (let [[sample] (tu/gene-samples 1)
        gene-id (first (gen/sample gsg/id 1))
        species (-> sample :gene/species :species/id)
        prod-seq-name (tu/seq-name-for-species species)
        data-sample (assoc sample
                           :gene/id gene-id
                           :gene/sequence-name (tu/seq-name-for-species species)
                           :gene/cgc-name (tu/cgc-name-for-species species)
                           :gene/status current-status)]
    [gene-id data-sample]))

(defn change-status
  [endpoint gene-id & {:keys [current-user method payload]
                       :or {current-user "tester@wormbase.org"
                            method :post
                            payload {}}}]
  (api-tc/send-request "gene"
                       method
                       payload
                       :sub-path (str gene-id endpoint)
                       :current-user current-user))

(defn kill-gene [gene-id]
  (change-status "" gene-id :method :delete))

(def resurrect-gene (partial change-status "/resurrect"))

(def suppress-gene (partial change-status "/suppress"))

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

(t/deftest killing
  (t/testing "kill and check right status in db after."
    (let [[gene-id sample] (gen-sample)]
      (tu/with-gene-fixtures
        sample
        (fn check-dead-after-kill [conn]
          (let [gene-id (:gene/id sample)
                [status body] (kill-gene gene-id)
                db (d/db conn)
                ent (d/entity db [:gene/id gene-id])]
            (tu/status-is? (:status (ok)) status body)
            (t/is (= (:gene/status ent) :gene.status/dead)))))))
  (t/testing "killed ok and provenance recorded."
    (let [[gene-id sample] (gen-sample)]
      (tu/with-gene-fixtures
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
                       "tester@wormbase.org")))
            (tu/status-is? (:status (ok)) status body)
            (t/is (= (:gene/status ent) :gene.status/dead)))))))
  (t/testing "Can kill a suppressed gene."
    (let [[gene-id sample] (gen-sample :current-status :gene.status/suppressed)]
      (tu/with-gene-fixtures
        sample
        (fn [conn]
          (let [[status body] (kill-gene gene-id)]
            (tu/status-is? (:status (ok)) status
                           body)))))))

(t/deftest ressurecting
  (t/testing "Resurrecting a dead gene succesfully"
    (let [[gene-id sample] (gen-sample :current-status :gene.status/dead)]
      (tu/with-gene-fixtures
        (assoc sample :gene/id gene-id)
        (fn [conn]
          (let [[status body] (resurrect-gene gene-id)]
            (tu/status-is? (:status (ok)) status body))))))
  (t/testing "Cannot resurrect live gene"
    (let [[gene-id sample] (gen-sample)]
      (tu/with-gene-fixtures
        (assoc sample :gene/id gene-id)
        (fn [conn]
          (let [[status body] (resurrect-gene gene-id)]
            (tu/status-is? (:status (precondition-failed)) status
                           body)))))))

(t/deftest suppressing
  (t/testing "Cannot suppress a dead gene."
    (let [[gene-id sample] (gen-sample :current-status :gene.status/dead)]
      (tu/with-gene-fixtures
        sample
        (fn [conn]
          (let [[status body] (suppress-gene gene-id)]
            (tu/status-is? (:status (precondition-failed)) status
                           body))))))
  (t/testing "Suppressing a live gene."
    (let [[gene-id sample] (gen-sample)]
      (tu/with-gene-fixtures
        sample
        (fn [conn]
          (let [[status body] (suppress-gene gene-id)]
            (tu/status-is? (:status (ok)) status body)))))))

