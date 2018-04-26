(ns integration.test-new-gene
  (:require
   [clojure.test :as t]
   [datomic.api :as d]
   [wormbase.api-test-client :as api-tc]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.species :as gss]
   [wormbase.names.service :as service]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def new-gene (partial api-tc/new "gene"))

(def not-nil? (complement nil?))

(defn check-db [db gene-id]
  (let [qr (d/q '[:find (count ?e) .
                  :in $ ?gid status
                  :where
                  [?e :gene/id ?gid]
                  [?e :gene/status ?status]]
                db
                gene-id
                :gene.status/live)]
    (t/is (not-nil? qr))))

(t/deftest must-meet-spec
  (t/testing "Incorrectly naming gene reports problems."
    (let [response (new-gene {})
          [status body] response]
      (tu/status-is? status 400 body)
      (t/is (contains? (tu/parse-body body) :message))))
  (t/testing "Species should always be required when creating gene name."
    (let [[status
           body] (new-gene {:gene/cgc-name (tu/cgc-name-for-species
                                            :species/c-elegans)})]
      (tu/status-is? status 400 (format "Body: " body)))))

(t/deftest wrong-data-shape
  (t/testing "Non-conformant data should result in HTTP Bad Request 400"
    (let [[status body] (new-gene {})]
      (tu/status-is? status 400 (format "Body: " body)))))

(t/deftest invalid-species-specified
  (t/testing "What happens when you specify an invalid species"
    (let [[status body] (new-gene
                         {:gene/cgc-name "abc-1"
                          :gene/species {:species/id :species/c-elegant}})]
      (tu/status-is? status 400 body))))

(t/deftest invalid-names
  (t/testing "Invalid CGC name for species causes validation error."
    (let [[status body] (new-gene
                         {:gene/cgc-name "_INVALID!_"
                          :gene/species {:species/id :species/c-elegans}})]
      (tu/status-is? status 400 body))))

(t/deftest naming-uncloned
  (t/testing "Naming one uncloned gene succesfully returns ids"
    (tu/with-gene-fixtures
      []
      (fn new-uncloned [conn]
        (let [[status body] (new-gene
                             {:gene/cgc-name (tu/cgc-name-for-species :species/c-elegans)
                              :gene/species {:species/id :species/c-elegans}})
              expected-id "WBGene00000001"]
          (tu/status-is? status 201 body)
          (let [db (d/db conn)
                identifier (some-> body :created :gene/id)]
            (t/is (= identifier expected-id))
            (check-db db identifier)
            (tu/query-provenance conn identifier :event/new-gene)))))))

(t/deftest naming-with-provenance
  (t/testing "Naming some genes providing provenance."
    (let [data {:gene/cgc-name (tu/cgc-name-for-species :species/c-elegans)
                :gene/species {:species/id :species/c-elegans}
                :provenance/who {:person/email
                                 "tester@wormbase.org"}}
          [status body] (new-gene data)]
      (tu/status-is? status 201 body)
      (t/is (some-> body :created :gene/id) (pr-str body)))))

