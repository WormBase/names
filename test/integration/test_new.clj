(ns integration.test-new
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov elegans-ln]]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.species :as gss]
   [wormbase.gen-specs.variation :as gsv]
   [wormbase.names.service :as service]
   [wormbase.test-utils :as tu]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.db :as wdb]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def new-gene (partial api-tc/new "gene"))

(def new-variation (partial api-tc/new "variation"))

(def new-species (partial api-tc/new "species"))

(def not-nil? (complement nil?))

(defn check-db [db ident id]
  (let [status-ident (keyword (namespace ident) "status")
        qr (d/q '[:find (count ?e) .
                  :in $ ?status-ident ?status ?id
                  :where
                  [?e :gene/id ?gid]
                  [?e :gene/status ?status]]
                db status-ident :gene.status/live id)]
    (t/is (not-nil? qr))))

(defn check-empty [create-fn]
  (let [response (create-fn {})
        [status body] response]
    (tu/status-is? 400 status body)
    (t/is (contains? (tu/parse-body body) :message))))

(t/deftest gene-data-must-meet-spec
  (t/testing "Empty gene data payload is a bad request."
    (check-empty new-gene))
  (t/testing "Species should always be required when creating gene name."
    (let [cgc-name (tu/cgc-name-for-species :species/c-elegans)
          [status body] (new-gene {:gene/cgc-name cgc-name})]
      (tu/status-is? 400 status body))))

(t/deftest wrong-gene-data-shape
  (t/testing "Non-conformant data gene should result in HTTP Bad Request 400"
    (let [[status body] (new-gene {})]
      (tu/status-is? 400 status body))))

(t/deftest invalid-gene-species-specified
  (t/testing "What happens when you specify an invalid species"
    (let [[status body] (new-gene
                         {:data {:gene/cgc-name "abc-1"
                                 :gene/species "Cabornot Elegant"}
                          :prov nil})]
      (tu/status-is? 400 status body))))

(t/deftest invalid-gene-names
  (t/testing "Invalid CGC name for species causes validation error."
    (let [[status body] (new-gene
                         {:data {:gene/cgc-name "_INVALID!_"
                                 :gene/species elegans-ln}
                          :prov nil})]
      (tu/status-is? 400 status body))))

(t/deftest naming-uncloned-gene
  (t/testing "Naming one uncloned gene succesfully returns ids"
    (tu/with-gene-fixtures
      []
      (fn new-uncloned [conn]
        (let [[status body] (new-gene
                             {:data {:gene/cgc-name (tu/cgc-name-for-species elegans-ln)
                                     :gene/species elegans-ln}
                              :prov nil})
              expected-id "WBGene00000001"]
          (tu/status-is? 201 status body)
          (let [db (d/db conn)
                identifier (some-> body :created :gene/id)]
            (t/is (= identifier expected-id))
            (check-db db :gene/id identifier)
            (tu/query-provenance conn identifier :event/new-gene)))))))

(t/deftest gene-naming-conflict
  (t/testing "When a gene already exists with the requested name."
    (let [cgc-name (tu/cgc-name-for-species elegans-ln)
          sample {:gene/cgc-name cgc-name
                  :gene/species elegans-ln
                  :gene/status :gene.status/live
                  :gene/id (first (gen/sample gsg/id 1))}
          data {:data (select-keys sample [:gene/cgc-name :gene/species])
                :prov basic-prov}]
      (tu/with-gene-fixtures
        sample
        (fn [conn]
          (let [[status body] (new-gene data)]
            (tu/status-is? 409 status body)))))))

(t/deftest naming-gene-with-provenance
  (t/testing "Naming some genes providing provenance."
    (let [data {:gene/cgc-name (tu/cgc-name-for-species elegans-ln)
                :gene/species elegans-ln}
          prov {:provenance/who {:person/email "tester@wormbase.org"}}
          [status body] (new-gene {:data data :prov prov})]
      (tu/status-is? 201 status body)
      (t/is (some-> body :created :gene/id) (pr-str body)))))

(t/deftest variation-data-must-meet-spec
  (t/testing "Empty gene data payload is a bad request."
    (check-empty new-variation))
  (t/testing "A new variation must be given a valid name."
    (let [vname "CONJURED_UP_123"
          [status body] (new-variation {:variation/name vname})]
      (tu/status-is? 400 status body))))

(t/deftest wrong-variation-data-shape
  (t/testing "Non-conformant variation data should result in HTTP Bad Request 400"
    (let [[status body] (new-variation {})]
      (tu/status-is? 400 status body))))

(t/deftest variation-naming-conflict
  (t/testing "When a variation already exists with the requested name."
    (let [vname (first (gen/sample gsv/name 1))
          sample {:variation/name vname
                  :variation/id (first (gen/sample gsv/id 1))
                  :variation/status :variation.status/live}
          data {:data (select-keys sample [:variation/name])
                :prov basic-prov}]
      (tu/with-fixtures
        sample
        (fn [conn]
          (let [[status body] (new-variation data)]
            (tu/status-is? 409 status body)))))))

(t/deftest naming-variation-with-provenance
  (t/testing "Naming a variation providing provenance."
    (let [data {:variation/name (first (gen/sample gsv/name 1))}
          [status body] (new-variation {:data data :prov basic-prov})]
      (tu/status-is? 201 status body)
      (t/is (some-> body :created :variation/id) (pr-str body)))))

(t/deftest species-data-must-meet-spec
  (t/testing "Empty species data payload is a bad request."
    (check-empty new-species))
  (t/testing "Species should always be required when creating gene name."
    (let [[status body] (new-species {:species/wrong-ident "Alpha alegator"})]
      (tu/status-is? 400 status body))))

(t/deftest wrong-species-data-shape
  (t/testing "Non-conformant species data should result in HTTP Bad Request 400"
    (let [[status body] (new-species {})]
      (tu/status-is? 400 status body))))

(t/deftest species-creation-success
  (t/testing "Create a new species, providing provenance."
    (let [data {:species/latin-name "Quantum squirmito"
                :species/cgc-name-pattern "^Q[a-z]{3}-[0-9]+"
                :species/sequence-name-pattern "^QSEQNAME_[0-9\\]+"}
          [status body] (new-species {:data data :prov basic-prov})]
      (tu/status-is? 201 status body)
      (let [dba (d/db wdb/conn)]
        (t/is (= (:species/id (d/pull dba [:species/id] (find data :species/latin-name)))
                 :species/q-squirmito))))))
