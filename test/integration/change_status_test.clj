(ns integration.change-status-test
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.gen-specs.variation :as gsv]
   [wormbase.names.util :as wnu]
   [wormbase.specs.agent :as wsa]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn- gene-sample [& {:keys [current-status]
                      :or {current-status :gene.status/live}}]
  (let [[sample] (tu/gene-samples 1)
        gene-id (first (gen/sample gsg/id 1))
        species (-> sample :gene/species second)
        data-sample (assoc sample
                           :gene/id gene-id
                           :gene/sequence-name (tu/seq-name-for-species species)
                           :gene/cgc-name (tu/cgc-name-for-species species)
                           :gene/status current-status)]
    [gene-id data-sample]))


(defn- variation-sample [& {:keys [current-status]
                            :or {current-status :variation.status/live}}]
  (let [[sample] (gen/sample gsv/payload 1)
        id (first (gen/sample gsv/id 1))
        data-sample (-> sample
                        (assoc :id id
                               :status current-status)
                        (wnu/qualify-keys "variation"))]
    [id data-sample]))

(defn change-gene-status
  [id & {:keys [current-user method payload _]
         :or {current-user "tester@wormbase.org"
              method :post
              payload {:prov nil}}}]
  (api-tc/send-request "gene"
                       method
                       payload
                       :sub-path id
                       :current-user current-user))

(defn change-entity-status
  [entity-type id & {:keys [current-user method payload endpoint]
                     :or {current-user "tester@wormbase.org"
                          method :post
                          endpoint nil
                          payload {:prov nil}}}]
  (let [uri* (str "/api/entity/" entity-type "/" id )]
    (api-tc/send-request nil
                         method
                         payload
                         :current-user current-user
                         :uri (if endpoint
                                (str uri* "/" endpoint)
                                uri*))))

(defn kill-gene [gene-id]
  (change-gene-status gene-id :method :delete))

(defn resurrect-gene [gene-id]
  (change-gene-status (str gene-id "/resurrect")))

(defn suppress-gene [gene-id]
  (change-gene-status (str gene-id "/suppress")))

(defn kill-variation [var-id]
  (change-entity-status "variation" var-id :method :delete))

(defn resurrect-variation [id]
  (change-entity-status "variation" id :endpoint "resurrect"))

(defn status-info [ident ^String status]
  (let [ent-ns (namespace ident)]
    [ent-ns
     (keyword ent-ns "status")
     (keyword (str ent-ns ".status") status)]))

(defn query-provenance [conn ident id]
  (let [[ent-ns status-key status-val] (status-info ident "dead")
        id-key (keyword ent-ns "id")
        lur [id-key id]]
    (when-let [tx-ids (d/q '[:find [?tx]
                             :in $ ?status-key ?status-val ?lur
                             :where
                             [?lur ?status-key ?status-val ?tx]]
                           (-> conn d/db d/history)
                           status-key
                           status-val
                           lur)]
      (map #(d/pull (d/db conn)
                    '[:provenance/why
                      :provenance/when
                      {:provenance/how [:db/ident]
                       :provenance/who [:person/email]}]
                    %)
           tx-ids))))

(defn kill-and-confirm-dead [conn kill-fn ident sample _]
  (let [id (ident sample)
        response (kill-fn id)
        db (d/db conn)
        ent (d/entity db [ident id])
        [_ status-key status-val] (status-info ident "dead")]
    (t/is (ru-hp/ok? response))
    (t/is (= (status-key ent) status-val))))

(defn check-prov-for-killed [conn kill-fn ident sample]
  (let [id (ident sample)
        response (kill-fn id)
        db (d/db conn)
        ent (d/entity db [ident id])
        [_ status-key status-val] (status-info ident "dead")
        _ (assert id (str "ID was nil:"  ident sample))
        provs (query-provenance conn ident id)]
    (t/is (= (count provs) 1))
    (let [prov (first provs)]
      (t/is (= (some-> prov :provenance/how :db/ident)
               :agent/console))
      (t/is (= (some-> prov :provenance/who :person/email)
               "tester@wormbase.org")))
    (t/is (ru-hp/ok? response))
    (t/is (= (status-key ent) status-val))))

(t/deftest killing-gene
  (t/testing "kill gene and check right status in db after."
    (let [[id sample] (gene-sample)]
      (tu/with-gene-fixtures
        sample
        (fn check-dead-after-kill [conn]
          (kill-and-confirm-dead conn kill-gene :gene/id sample id)))))
  (t/testing "killed ok and provenance recorded."
    (let [[_ sample] (gene-sample)]
      (tu/with-gene-fixtures
        sample
        (fn check-prov-after-kill [conn]
          (check-prov-for-killed conn kill-gene :gene/id sample)))))
  (t/testing "Can kill a suppressed gene."
    (let [[id sample] (gene-sample :current-status :gene.status/suppressed)]
      (tu/with-gene-fixtures
        sample
        (fn [_]
          (let [response (kill-gene id)]
            (t/is (ru-hp/ok? response))))))))

(t/deftest ressurecting-gene
  (t/testing "Resurrecting a dead gene succesfully"
    (let [[id sample] (gene-sample :current-status :gene.status/dead)]
      (tu/with-gene-fixtures
        (assoc sample :gene/id id)
        (fn [_]
          (let [response (resurrect-gene id)]
            (t/is (ru-hp/ok? response)))))))
  (t/testing "Cannot resurrect live gene"
    (let [[id sample] (gene-sample)]
      (tu/with-gene-fixtures
        (assoc sample :gene/id id)
        (fn [_]
          (let [response (resurrect-gene id)]
            (t/is (ru-hp/bad-request? response))))))))

(t/deftest suppressing-gene
  (t/testing "Cannot suppress a dead gene."
    (let [[id sample] (gene-sample :current-status :gene.status/dead)]
      (tu/with-gene-fixtures
        sample
        (fn [_]
          (let [response (suppress-gene id)]
            (t/is (ru-hp/bad-request? response)))))))
  (t/testing "Suppressing a live gene."
    (let [[id sample] (gene-sample)]
      (tu/with-gene-fixtures
        sample
        (fn [_]
          (let [response (suppress-gene id)]
            (t/is (ru-hp/ok? response))))))))

(t/deftest killing-varation
  (t/testing "kill variation and check right status in db after."
    (let [[id sample] (variation-sample)]
      (tu/with-variation-fixtures
        sample
        (fn check-dead-after-kill [conn]
          (kill-and-confirm-dead conn kill-variation :variation/id sample id)))))
  (t/testing "killed variation ok and provenance recorded."
    (let [[_ sample] (variation-sample)]
      (tu/with-variation-fixtures
        sample
        (fn check-dead-after-kill [conn]
          (check-prov-for-killed conn kill-variation :variation/id sample))))))

(t/deftest ressurecting-variation
  (t/testing "Resurrecting a dead variation succesfully"
    (let [[id sample] (variation-sample :current-status :variation.status/dead)]
      (tu/with-variation-fixtures
        [(assoc sample :variation/id id)]
        (fn [_]
          (let [response (resurrect-variation id)]
            (t/is (ru-hp/ok? response)))))))
  (t/testing "Cannot resurrect live variation"
    (let [[id sample] (variation-sample)]
      (tu/with-variation-fixtures
        (assoc sample :variation/id id)
        (fn [_]
          (let [response (resurrect-variation id)]
            (t/is (ru-hp/bad-request? response))))))))
