(ns integration.test-update
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [wormbase.constdata :refer [basic-prov]]
   [wormbase.db :as wdb]
   [wormbase.db-testing :as db-testing]
   [wormbase.fake-auth] ;; for side effect
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.gen-specs.species :as gss]
   [wormbase.gen-specs.variation :as gsv]
   [wormbase.names.service :as service]
   [wormbase.specs.gene :as wsg]
   [wormbase.test-utils :as tu]
   [wormbase.api-test-client :as api-tc]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def update-gene (partial api-tc/update "gene"))

(t/deftest must-meet-spec
  (let [identifier (first (gen/sample gsg/id 1))
        sample (-> (tu/gen-sample gsg/cloned 1)
                   (first)
                   (tu/species->ref)
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
              (tu/status-is? 400 status (pr-str response)))))))))

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

(t/deftest update-uncloned
  (t/testing "Naming one uncloned gene succesfully."
    (let [gid (first (gen/sample gsg/id 1))
          sample (-> (tu/gen-sample gsg/uncloned 1)
                     first
                     tu/species->ref
                     (select-keys [:gene/cgc-name :gene/species]))
          sample-data (assoc sample
                             :gene/id gid
                             :gene/cgc-name (tu/cgc-name-for-sample sample))]
      (tu/with-gene-fixtures
        [sample-data]
        (fn do-update [conn]
          (let [new-cgc-name (tu/cgc-name-for-sample sample-data)
                species (tu/species-ref->latin-name sample)
                payload {:data (-> sample-data
                                   (assoc :gene/species species)
                                   (dissoc :gene/id)
                                   (assoc :gene/cgc-name new-cgc-name))
                         :prov nil}
                [status body] (update-gene gid payload)]
            (tu/status-is? 200 status body)
            (let [db (d/db conn)
                  gid-2 (some-> body :updated :gene/id)
                  updated (:updated body)]
              (t/is (= (:gene/species updated)
                       (get-in payload [:data :gene/species])))
              (t/is (= (:gene/cgc-name updated) new-cgc-name))
              (tu/query-provenance conn gid-2 :event/update-gene))))))))

(t/deftest removing-cgc-name-from-cloned-gene
  (t/testing (str "Allow CGC name to be removed from a cloned gene.")
    (let [gid (first (gen/sample gsg/id 1))
          sample (-> (tu/gen-sample gsg/cloned 1)
                     first
                     tu/species->ref
                     (select-keys [:gene/cgc-name :gene/species]))
          species (tu/species-ref->latin-name sample)
          sample-data (assoc sample
                             :gene/id gid
                             :gene/biotype :biotype/cds
                             :gene/sequence-name (tu/seq-name-for-sample sample)
                             :gene/cgc-name (tu/cgc-name-for-sample sample))]
      (tu/with-gene-fixtures
        [sample-data]
        (fn do-update [conn]
          (let [payload {:data (-> sample-data
                                   (assoc :gene/species species)
                                   (dissoc :gene/id)
                                   (assoc :gene/cgc-name nil))
                         :prov nil}
                [status body] (update-gene gid payload)]
            (tu/status-is? 200 status body)
            (let [db (d/db conn)
                  identifier (some-> body :updated :gene/id)
                  updated (:updated body)]
              (t/is (empty? (:gene/cgc-name body)))
              (tu/query-provenance conn identifier :event/update-gene))))))))

(t/deftest gene-provenance
  (t/testing (str "Provenance is recorded for successful transactions")
   (let [gid (first (gen/sample gsg/id 1))
         sample (-> (tu/gen-sample gsg/cloned 1) first tu/species->ref)
         orig-cgc-name (tu/cgc-name-for-sample sample)
         sample-data (merge
                      sample
                      {:gene/id gid
                       :gene/cgc-name orig-cgc-name
                       :gene/status :gene.status/live
                       :gene/sequence-name (tu/seq-name-for-sample sample)})]
     (tu/with-gene-fixtures
       sample-data
       (fn [conn]
         (let [new-cgc-name (tu/cgc-name-for-sample sample-data)
               why "udpate prov test"
               species (tu/species-ref->latin-name sample-data)
               payload {:data  (-> sample-data
                                   (assoc :gene/species species)
                                   (dissoc :gene/id :gene/status)
                                   (assoc :gene/cgc-name new-cgc-name))
                        :prov {:provenance/why why
                               :provenance/who {:person/email "tester@wormbase.org"}}}]
           (let [response (update-gene gid payload)
                 [status body] response
                 db (d/db conn)
                 ent (d/entity db [:gene/id gid])]
             (tu/status-is? 200 status body)
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

(def update-variation (partial api-tc/update "variation"))

(t/deftest variation-data-must-meet-spec
  (let [identifier (first (gen/sample gsv/id 1))
        sample {:variation/name (first (gen/sample gsv/name 1))
                :variation/status :variation.status/live}
        sample-data (merge sample {:variation/id identifier})]
    (tu/with-fixtures
      sample-data
      (fn [conn]
        (t/testing (str "Updating name for existing variation requires "
                        "correct data structure.")
          (let [payload {:data {} :prov basic-prov}]
            (let [response (update-variation identifier payload)
                  [status body] response]
              (tu/status-is? 400 status (pr-str response)))))))))

(t/deftest changing-variation-name-success
  (t/testing "Changing the name of an existing variation."
    (let [identifier (first (gen/sample gsv/id 1))
          sample {:variation/name (first (gen/sample gsv/name 1))
                  :variation/status :variation.status/live}
          sample-data (merge sample {:variation/id identifier})]
      (tu/with-fixtures
        sample-data
        (fn [conn]
          (let [data {:variation/name "mynew1"}
                payload {:data data :prov basic-prov}
                [status body] (update-variation identifier payload)]
            (tu/status-is? 200 status body)
            (t/is (= (:variation/name (d/pull (d/db conn)
                                              '[:variation/name]
                                              [:variation/id identifier]))
                     "mynew1"))))))))

(t/deftest changing-variation-name-to-non-uniq
  (t/testing "Changing the name of a variation to something that's already taken."
    (let [v-names (gen/sample gsv/name 2)
          samples (map (fn [vname]
                         {:variation/name vname
                          :variation/id (first (gen/sample gsv/id 1))
                          :variation/status :variation.status/live})
                       v-names)
          subject-identifier (-> samples last :variation/id)]
      (tu/with-fixtures
        samples
        (fn [conn]
          (let [data {:variation/name (-> samples first :variation/name)}
                payload {:data data :prov basic-prov}
                [status body] (update-variation subject-identifier payload)]
            (tu/status-is? 409 status body)))))))
