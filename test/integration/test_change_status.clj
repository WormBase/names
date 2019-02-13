(ns integration.test-change-status
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-response :refer [ok bad-request]]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.gen-specs.variation :as gsv]
   [wormbase.names.service :as service]
   [wormbase.specs.agent :as wsa]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn- gene-sample [& {:keys [current-status]
                      :or {current-status :gene.status/live}}]
  (let [[sample] (tu/gene-samples 1)
        gene-id (first (gen/sample gsg/id 1))
        species (-> sample :gene/species second)
        prod-seq-name (tu/seq-name-for-species species)
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
        data-sample (assoc sample
                           :variation/id id
                           :variation/status current-status)]
    [id data-sample]))

(defn change-status
  [entity-type endpoint id & {:keys [current-user method payload]
                              :or {current-user "tester@wormbase.org"
                                   method :post
                                   payload {:prov nil}}}]
  (api-tc/send-request entity-type
                       method
                       payload
                       :sub-path (str id endpoint)
                       :current-user current-user))

(defn kill-gene [gene-id]
  (change-status "gene" "" gene-id :method :delete))

(def resurrect-gene (partial change-status "gene" "/resurrect"))

(def suppress-gene (partial change-status "gene" "/suppress"))

(defn kill-variation [var-id]
  (change-status "variation" "" var-id :method :delete))

(def resurrect-variation (partial change-status "variation" "/resurrect"))

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

(defn check-killed-dead [conn kill-fn ident sample id]
  (let [id (ident sample)
        [status body] (kill-fn id)
        db (d/db conn)
        ent (d/entity db [ident id])
        [_ status-key status-val] (status-info ident "dead")]
    (tu/status-is? (:status (ok)) status body)
    (t/is (= (status-key ent) status-val))))

(defn check-prov-for-killed [conn kill-fn ident sample]
  (let [id (ident sample)
        [status body] (kill-fn id)
        db (d/db conn)
        ent (d/entity db [ident id])
        [_ status-key status-val] (status-info ident "dead")
        _ (assert id (str "ID was nil:"  ident sample))
        provs (query-provenance conn ident id)]
    (t/is (= (count provs) 1))
    (let [prov (first provs)]
      (t/is (= (some-> prov :provenance/how :db/ident)
               :agent/web))
      (t/is (= (some-> prov :provenance/who :person/email)
               "tester@wormbase.org")))
    (tu/status-is? (:status (ok)) status body)
    (t/is (= (status-key ent) status-val))))

(t/deftest killing-gene
  (t/testing "kill gene and check right status in db after."
    (let [[id sample] (gene-sample)]
      (tu/with-gene-fixtures
        sample
        (fn check-dead-after-kill [conn]
          (check-killed-dead conn kill-gene :gene/id sample id)))))
  (t/testing "killed ok and provenance recorded."
    (let [[id sample] (gene-sample)]
      (tu/with-gene-fixtures
        sample
        (fn check-prov-after-kill [conn]
          (check-prov-for-killed conn kill-gene :gene/id sample)))))
  (t/testing "Can kill a suppressed gene."
    (let [[id sample] (gene-sample :current-status :gene.status/suppressed)]
      (tu/with-gene-fixtures
        sample
        (fn [conn]
          (let [[status body] (kill-gene id)]
            (tu/status-is? (:status (ok)) status body)))))))

(t/deftest ressurecting-gene
  (t/testing "Resurrecting a dead gene succesfully"
    (let [[id sample] (gene-sample :current-status :gene.status/dead)]
      (tu/with-gene-fixtures
        (assoc sample :gene/id id)
        (fn [conn]
          (let [[status body] (resurrect-gene id)]
            (tu/status-is? (:status (ok)) status body))))))
  (t/testing "Cannot resurrect live gene"
    (let [[id sample] (gene-sample)]
      (tu/with-gene-fixtures
        (assoc sample :gene/id id)
        (fn [conn]
          (let [[status body] (resurrect-gene id)]
            (tu/status-is? (:status (bad-request)) status body)))))))

(t/deftest suppressing-gene
  (t/testing "Cannot suppress a dead gene."
    (let [[id sample] (gene-sample :current-status :gene.status/dead)]
      (tu/with-gene-fixtures
        sample
        (fn [conn]
          (let [[status body] (suppress-gene id)]
            (tu/status-is? (:status (bad-request)) status body))))))
  (t/testing "Suppressing a live gene."
    (let [[id sample] (gene-sample)]
      (tu/with-gene-fixtures
        sample
        (fn [conn]
          (let [[status body] (suppress-gene id)]
            (tu/status-is? (:status (ok)) status body)))))))

(t/deftest killing-varation
  (t/testing "kill variation and check right status in db after."
    (let [[id sample] (variation-sample)]
      (tu/with-fixtures
        sample
        (fn check-dead-after-kill [conn]
          (check-killed-dead conn kill-variation :variation/id sample id)))))
  (t/testing "killed variation ok and provenance recorded."
    (let [[id sample] (variation-sample)]
      (tu/with-fixtures
        sample
        (fn check-dead-after-kill [conn]
          (check-prov-for-killed conn kill-variation :variation/id sample))))))

(t/deftest ressurecting-variation
  (t/testing "Resurrecting a dead variation succesfully"
    (let [[id sample] (variation-sample :current-status :variation.status/dead)]
      (println "SAMPLE FOR RESURRECT " id)
      (prn sample)
      (tu/with-fixtures
        (assoc sample :variation/id id)
        (fn [conn]
          (let [[status body] (resurrect-variation id)]
            (tu/status-is? (:status (ok)) status body))))))
  (t/testing "Cannot resurrect live gene"
    (let [[id sample] (variation-sample)]
      (tu/with-fixtures
        (assoc sample :variation/id id)
        (fn [conn]
          (let [[status body] (resurrect-variation id)]
            (tu/status-is? (:status (bad-request)) status body)))))))
