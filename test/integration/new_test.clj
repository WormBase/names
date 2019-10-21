(ns integration.new-test
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov elegans-ln]]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.species :as gss]
   [wormbase.gen-specs.variation :as gsv]
   [wormbase.names.entity :as wne]
   [wormbase.names.service :as service]
   [wormbase.names.util :as wnu]
   [wormbase.test-utils :as tu]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.db :as wdb]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def new-gene (partial api-tc/new "gene"))

(def new-species (partial api-tc/new "species"))

(def not-nil? (complement nil?))

(defn new-variation [& args]
  (apply api-tc/new "entity/variation" args))

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
  (let [response (create-fn {})]
    (t/is (ru-hp/bad-request? response))
    (t/is (contains? (get response :body {}) :message))))

(t/deftest gene-data-must-meet-spec
  (t/testing "Empty gene data payload is a bad request."
    (check-empty new-gene))
  (t/testing "Species should always be required when creating gene name."
    (let [cgc-name (tu/cgc-name-for-species "c-elegans")
          response (new-gene {:gene/cgc-name cgc-name})]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest wrong-gene-data-shape
  (t/testing "Non-conformant data gene should result in HTTP Bad Request 400"
    (let [response (new-gene {})]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest invalid-gene-species-specified
  (t/testing "What happens when you specify an invalid species"
    (let [response (new-gene
                    {:data {:gene/cgc-name "abc-1"
                            :gene/species "Cabornot Elegant"}
                     :prov nil})]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest invalid-gene-names
  (t/testing "Invalid CGC name for species causes validation error."
    (let [response (new-gene
                    {:data {:gene/cgc-name "_INVALID!_"
                            :gene/species elegans-ln}
                     :prov nil})]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest naming-uncloned-gene
  (t/testing "Naming one uncloned gene succesfully returns ids"
    (tu/with-gene-fixtures
      []
      (fn new-uncloned [conn]
        (let [response (new-gene
                        {:data {:cgc-name (tu/cgc-name-for-species elegans-ln)
                                :species elegans-ln}
                         :prov nil})
              expected-id "WBGene00000001"]
          (t/is (ru-hp/created? response))
          (let [db (d/db conn)
                identifier (some-> response :body :created :id)]
            (t/is (= identifier expected-id))
            (check-db db :gene/id identifier)
            (tu/query-provenance conn identifier :event/new-gene)))))))

(t/deftest naming-cloned-gene
  (t/testing "Naming one cloned gene succesfully returns ids"
    (tu/with-gene-fixtures
      []
      (fn new-uncloned [conn]
        (let [response (new-gene
                        {:data {:sequence-name (tu/seq-name-for-species elegans-ln)
                                :biotype "cds"
                                :species elegans-ln}
                         :prov nil})
              expected-id "WBGene00000001"]
          (t/is (ru-hp/created? response))
          (let [db (d/db conn)
                identifier (some-> response :body :created :id)]
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
          data {:data (-> sample
                          (select-keys [:gene/cgc-name :gene/species])
                          (wnu/unqualify-keys "gene"))
                :prov basic-prov}]
      (tu/with-gene-fixtures
        sample
        (fn [conn]
          (let [response (new-gene data)]
            (t/is (ru-hp/conflict? response))))))))

(t/deftest naming-gene-with-provenance
  (t/testing "Naming some genes providing provenance."
    (let [data {:cgc-name (tu/cgc-name-for-species elegans-ln)
                :species elegans-ln}
          prov {:who {:email "tester@wormbase.org"}}
          response (new-gene {:data data :prov prov})]
      (t/is (ru-hp/created? response))
      (t/is (some-> response :body :created :id) (pr-str response)))))

(t/deftest naming-gene-bypass-nomenclature
  (t/testing "Bypassing nomenclature validation when creating gene is ok."
    (let [data {:cgc-name "AnythingILike123"
                :species elegans-ln}
          prov {:who {:email "tester@wormbase.org"}}
          response (new-gene {:data data :prov prov :force true})]
      (t/is (ru-hp/created? response))
      (t/is (some-> response :body :created :id) (pr-str response)))))

(t/deftest variation-data-must-meet-spec
  (t/testing "Empty gene data payload is a bad request."
    (check-empty new-variation))
  (t/testing "A new variation must be given a valid name."
    (let [vname "CONJURED_UP_123"
          data {:data {:variation/name vname} :prov basic-prov}
          response (new-variation data)]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest wrong-variation-data-shape
  (t/testing "Non-conformant variation data should result in HTTP Bad Request 400"
    (tu/with-installed-generic-entity
      :variation/id
      "WBVar%08d"
      (fn [_]
        (let [response (new-variation {})]
          (t/is (ru-hp/bad-request? response)))))))

(t/deftest variation-naming-conflict
  (t/testing "When a variation already exists with the requested name."
    (let [vname (first (gen/sample gsv/name 1))
          sample {:variation/name vname
                  :variation/id (first (gen/sample gsv/id 1))
                  :variation/status :variation.status/live}
          data {:data {:name vname}
                :prov basic-prov}]
      (tu/with-fixtures
        sample
        (fn [conn]
          (let [response (new-variation data)]
            (t/is (ru-hp/conflict? response))))))))

(t/deftest naming-variation-with-provenance
  (t/testing "Naming a variation providing provenance."
    (tu/with-installed-generic-entity
      :variation/id
      "WBVar%08d"
      (fn [_]
        (let [data {:name (first (gen/sample gsv/name 1))}
              response (new-variation {:data data :prov basic-prov})]
          (t/is (ru-hp/created? response))
          (t/is (some-> response :body :created :id) (pr-str response)))))))

(t/deftest species-data-must-meet-spec
  (t/testing "Empty species data payload is a bad request."
    (check-empty new-species))
  (t/testing "Species should always be required when creating gene name."
    (let [response (new-species {:species/wrong-ident "Alpha alegator"})]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest wrong-species-data-shape
  (t/testing "Non-conformant species data should result in HTTP Bad Request 400"
    (let [response (new-species {})]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest species-creation-success
  (t/testing "Create a new species, providing provenance."
    (let [data {:species/latin-name "Quantum squirmito"
                :species/cgc-name-pattern "^Q[a-z]{3}-[0-9]+"
                :species/sequence-name-pattern "^QSEQNAME_[0-9\\]+"}
          response (new-species {:data (wnu/unqualify-keys data "species")
                                 :prov basic-prov})]
      (t/is (ru-hp/created? response) (pr-str response))
      (let [dba (d/db wdb/conn)]
        (t/is (= (:species/id (d/pull dba [:species/id] (find data :species/latin-name)))
                 :species/q-squirmito))))))
