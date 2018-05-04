(ns integration.test-update-gene
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [wormbase.db :as owdb]
   [wormbase.db-testing :as db-testing]
   [wormbase.fake-auth] ;; for side effect
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.gen-specs.species :as gss]
   [wormbase.names.service :as service]
   [wormbase.specs.gene :as owsg]
   [wormbase.test-utils :as tu]
   [wormbase.api-test-client :as api-tc]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def update-gene (partial api-tc/update "gene"))

(t/deftest must-meet-spec
  (let [identifier (first (gen/sample gsg/id 1))
        sample (-> (gen/sample gsg/update 1)
                   (first)
                   (assoc :provenance/who "tester@wormbase.org"))
        sample-data (merge sample {:gene/id identifier})]
    (tu/with-gene-fixtures
      sample-data
      (fn [conn]
        (t/testing (str "Updating name for existing gene requires "
                        "correct data structure.")
          (let [data {}]
            (let [response (update-gene identifier data)
                  [status body] response]
              (tu/status-is? status 400 (pr-str response)))))))))

(defn query-provenance [conn changed-attr]
  (when-let [tx-ids (d/q '[:find [?tx]
                           :in $ ?event ?changed-attr
                           :where
                           [_ ?changed-attr]
                           [?ev :db/ident ?event]
                           [?tx :provenance/what ?ev]]
                         (-> conn d/db d/history)
                         :event/update-gene
                         changed-attr)]
    (map #(d/pull (d/db conn)
                  '[*
                    {:provenance/how [:db/ident]
                     :provenance/what [:db/ident]
                     :provenance/who [:person/email]}]
                  %)
         tx-ids)))

(t/deftest provenance
  (t/testing (str "Provenance is recorded for successful transactions")
    (let [identifier (first (gen/sample gsg/id 1))
          sample (first (gen/sample gsg/update 1))
          orig-cgc-name (tu/cgc-name-for-sample sample)
          sample-data (merge
                       sample
                       {:gene/id identifier
                        :gene/cgc-name orig-cgc-name
                        :gene/status :gene.status/live}
                       (when (contains? sample :gene/sequence-name)
                         {:gene/sequence-name (tu/seq-name-for-sample sample)}))
          species-id (:species/id sample-data)]
      (tu/with-gene-fixtures
        sample-data
        (fn [conn]
          (let [new-cgc-name (tu/cgc-name-for-sample sample-data)
                why "udpate prov test"
                payload (-> sample-data
                            (dissoc :gene/status)
                            (assoc :gene/cgc-name new-cgc-name)
                            (assoc :provenance/who
                                   {:person/email "tester@wormbase.org"})
                            (assoc :provenance/why
                                   why))]
            (let [response (update-gene identifier payload)
                  [status body] response
                  db (d/db conn)
                  ent (d/entity db [:gene/id identifier])]
              (tu/status-is? status 200 body)
              (let [provs (query-provenance conn :gene/cgc-name)
                    act-prov (first provs)]
                (t/is (= (-> act-prov :provenance/what :db/ident) :event/update-gene)
                      (pr-str act-prov))
                (t/is (= (-> act-prov :provenance/how :db/ident) :agent/web)
                      (pr-str act-prov))
                (t/is (= (:provenance/why act-prov) why))
                (t/is (= (-> act-prov :provenance/who :person/email)
                         "tester@wormbase.org"))
                (t/is (not= nil (:provenance/when act-prov))))
              (let [gs (:gene/status ent)]
                (t/is (= :gene.status/live gs)
                      (pr-str (:gene/status ent))))
              (t/is (not= orig-cgc-name (:gene/cgc-name ent)))
              (t/is (= new-cgc-name (:gene/cgc-name ent))))))))))
