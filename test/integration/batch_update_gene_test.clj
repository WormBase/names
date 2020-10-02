(ns integration.batch-update-gene-test
  (:require
   [clojure.set :as cset]
   [clojure.string :as str]
   [clojure.test :as t]
   [clj-uuid :as uuid]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov]]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn get-gene-info [gid]
  (api-tc/summary "gene" gid))

(defn update-genes [data]
  (api-tc/send-request "batch" :put data :sub-path "gene"))

(defn add-other-names-gene [data]
  (api-tc/send-request "batch" :put data :sub-path "gene/update-other-names"))
(defn retract-other-names-gene [data]
  (api-tc/send-request "batch" :delete data :sub-path "gene/update-other-names"))

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (let [response (update-genes {:data [] :prov nil})]
      (t/is (ru-hp/bad-request? response)))))

(t/deftest non-uniq-names
  (t/testing "Batch with multiple items containing non-unique names is rejected."
    (let [fixtures (tu/gene-samples 2)
          [g1 g2] fixtures]
      (tu/with-gene-fixtures
        fixtures
        (fn [_]
          (let [bdata [{:gene/id (:gene/id g1)
                        :gene/cgc-name "dup-1"}
                       {:gene/id (:gene/id g2)
                        :gene/cgc-name "dup-1"}]
                response (update-genes {:data bdata :prov basic-prov})]
            (t/is (ru-hp/bad-request? response))))))))

(t/deftest invalid-cgc-name
  (t/testing "Errors are reported when provided with a invlaid CGC name"
    (let [fixtures (tu/gene-samples 2)
          [g1 _] fixtures]
      (tu/with-gene-fixtures
        fixtures
        (fn [_]
          (let [bdata [{:gene/id (:gene/id g1)
                        :gene/cgc-name "invalid_CGC_NAME"}]
                response (update-genes {:data bdata :prov basic-prov})]
            (t/is (ru-hp/bad-request? response))))))))

(t/deftest invalid-sequence-name
  (t/testing "Errors are reported when provided with a invlaid Sequence name"
    (let [fixtures (tu/gene-samples 2)
          [g1 _] fixtures]
      (tu/with-gene-fixtures
        fixtures
        (fn [_]
          (let [bdata [{:gene/id (:gene/id g1)
                        :gene/sequence-name "invalid-sequence-name.1"}]
                response (update-genes {:data bdata :prov basic-prov})]
            (t/is (ru-hp/bad-request? response))))))))

(t/deftest genes-invalid-species
  (t/testing "Batch with invalid species is rejected."
    (let [fixtures (tu/gene-samples 1)
          gid (-> fixtures first :gene/id)
          bad-species "Caenorhabditis donkey"]
      (tu/with-gene-fixtures
        fixtures
        (fn [_]
          (let [bdata [{:cgc-name "duqp-1"
                        :species bad-species
                        :id gid}]
                response (update-genes {:data bdata :prov basic-prov})]
            (t/is (ru-hp/bad-request? response))
            (t/is (str/includes? (get-in response [:body :message] "") "Invalid species"))))))))

(t/deftest single-item
  (let [fixtures (tu/gene-samples 1)
        gene-rec (first fixtures)]
    (tu/with-gene-fixtures
      fixtures
      (fn [_]
        (t/testing "Batch with one item accepted, returns batch id."
          (let [species (-> gene-rec :gene/species second)
                bdata [(merge {:species species
                               :cgc-name (tu/cgc-name-for-species species)}
                              {:id (:gene/id gene-rec)})]
                payload {:data bdata :prov basic-prov}
                response (update-genes payload)]
            (t/is (ru-hp/ok? response))
            (t/is (get-in response [:body :updated :id]) (pr-str response))))))))

(t/deftest multi-item
  (t/testing "Batch with a random number of items is successful"
    (let [[g1 g2] (tu/gene-samples 2)
          sp-1 (-> g1 :gene/species second)
          sp-2 (-> g2 :gene/species second)
          bdata [{:cgc-name (tu/cgc-name-for-species sp-1)
                  :id (:gene/id g1)
                  :species sp-1}
                 {:sequence-name (tu/seq-name-for-species sp-2)
                  :biotype "cds"
                  :id (:gene/id g2)
                  :species sp-2}]]
      (tu/with-gene-fixtures
        [g1 g2]
        (fn [_]
          (let [response (update-genes {:data bdata :prov basic-prov})]
            (t/is (ru-hp/ok? response) (pr-str response))
            (let [bid (get-in response [:body :updated :id] "")]
              (t/is (uuid/uuid-string? bid)))))))))

(t/deftest update-uncloned-gene-with-cgc-name
  (t/testing "Test updating an existing clone gene adding just the cgc-name with a specific payload."
    (let [samples [#:gene{:species [:species/latin-name "Caenorhabditis elegans"]
                          :sequence-name "F23H11.5"
                          :biotype :biotype/cds
                          :id "WBGene00000263"}]]
      (tu/with-gene-fixtures
        samples
        (fn [_]
          (let [response (update-genes {:prov {},
                                        :data [{:species "Caenorhabditis elegans",
                                                :cgc-name "xxx-1",
                                                :id "WBGene00000263"}]})]
            (t/is (ru-hp/ok? response))))))))

(t/deftest update-gene-other-names
  (t/testing "Testing successful addition to / removal from gene's other-names."
    ; Test successful PUT /batch/gene/update-other-names operation
    (let [[g1 g2] (tu/gene-samples 2)
          g1-extra-names (gsg/gen-other-names 2)
          g2-extra-names (gsg/gen-other-names 2)
          g1-other-names (or (:gene/other-names g1) [])
          g2-other-names (or (:gene/other-names g2) [])
          bdata [{:id (:gene/id g1)
                  :other-names g1-extra-names}
                 {:id (:gene/id g2)
                  :other-names g2-extra-names}]]
      (tu/with-gene-fixtures
        [g1 g2]
        (fn [_]
          (let [response (add-other-names-gene {:data bdata :prov basic-prov})]
            (t/is (ru-hp/ok? response) (pr-str response))
            (let [bid (get-in response [:body :added :id] "")]
              (t/is (uuid/uuid-string? bid))
              ; Compare successful PUT /batch/gene/update-other-names operation result (gene objects) to expected results
              (let [g1-add-result (get-in (get-gene-info (:gene/id g1)) [:body :other-names])
                    g2-add-result (get-in (get-gene-info (:gene/id g2)) [:body :other-names])
                    g1-expected-add-result (distinct (concat g1-other-names g1-extra-names))
                    g2-expected-add-result (distinct (concat g2-other-names g2-extra-names))]

                (t/is (= (set g1-add-result) (set g1-expected-add-result)) "Add-other-name resulted in unexpected g1 object")
                (t/is (= (set g2-add-result) (set g2-expected-add-result)) "Add-other-name resulted in unexpected g2 object")
                ; Test successful DELETE /batch/gene/update-other-names operation
                (let [g1-n (- (count g1-add-result) 1)
                      g2-n (- (count g2-add-result) 1)
                      g1-retract-names (take g1-n (shuffle g1-add-result))
                      g2-retract-names (take g2-n (shuffle g2-add-result))
                      bdata [{:id (:gene/id g1)
                              :other-names g1-retract-names}
                             {:id (:gene/id g2)
                              :other-names g2-retract-names}]
                      response (retract-other-names-gene {:data bdata :prov basic-prov})]

                  (t/is (ru-hp/ok? response) (pr-str response))
                  (let [bid (get-in response [:body :retracted :id] "")]
                    (t/is (uuid/uuid-string? bid))
                    ; Compare successful DELETE /batch/gene/update-other-names operation result (gene objects) to expected results
                    (let [g1-retract-result (get-in (get-gene-info (:gene/id g1)) [:body :other-names])
                          g2-retract-result (get-in (get-gene-info (:gene/id g2)) [:body :other-names])
                          g1-expected-retract-result (cset/difference (set g1-add-result) (set g1-retract-names))
                          g2-expected-retract-result (cset/difference (set g2-add-result) (set g2-retract-names))]

                      (t/is (= (set g1-retract-result) (set g1-expected-retract-result)) "Retract-other-name resulted in unexpected g1 object")
                      (t/is (= (set g2-retract-result) (set g2-expected-retract-result)) "Retract-other-name resulted in unexpected g2 object")
                      (t/is (= (count g1-retract-result) 1) "Unexpected g1 retract other-names result-size")
                      (t/is (= (count g2-retract-result) 1) "Unexpected g2 retract other-names result-size"))))))))))))

(t/deftest update-does-not-require-species
  (t/testing "Test updating a gene does not require a species."
    (let [samples [#:gene{:species [:species/latin-name "Caenorhabditis elegans"]
                          :sequence-name "F23H11.5"
                          :biotype :biotype/cds
                          :id "WBGene00000263"}]]
      (tu/with-gene-fixtures
        samples
        (fn [_]
          (let [response (update-genes {:prov {},
                                        :data [{:cgc-name "xxx-1" :id "WBGene00000263"}]})]
            (t/is (ru-hp/ok? response))))))))
