(ns integration.update-test
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [clojure.set :as cset]
   [datomic.api :as d]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.constdata :refer [basic-prov]]
   [wormbase.db-testing :as db-testing]
   [wormbase.fake-auth] ;; for side effect
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.gen-specs.species :as gss]
   [wormbase.gen-specs.variation :as gsv]
   [wormbase.names.entity :as wne]
   [wormbase.names.species :as wns]
   [wormbase.names.util :as wnu]
   [wormbase.specs.gene :as wsg]
   [wormbase.test-utils :as tu]
   [wormbase.api-test-client :as api-tc]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def update-gene (partial api-tc/update "gene"))

(def update-variation (partial api-tc/update "entity/variation"))

(def update-species (partial api-tc/update "species"))

(t/deftest must-meet-spec
  (let [identifier (first (gen/sample gsg/id 1))
        sample (-> (tu/gen-sample gsg/cloned 1)
                   (first)
                   (wnu/qualify-keys "gene")
                   (wne/transform-ident-ref-values)
                   (tu/species->ref)
                   (assoc :provenance/who "tester@wormbase.org"))
        sample-data (merge sample {:gene/id identifier})]
    (tu/with-gene-fixtures
      sample-data
      (fn [_]
        (t/testing (str "Updating name for existing gene requires "
                        "correct data structure.")
          (let [data {}
                response (update-gene identifier data)]
            (t/is (ru-hp/bad-request? response))))))))

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
          sample* (first (tu/gen-sample gsg/uncloned 1))
          sample (-> sample*
                     (wnu/qualify-keys "gene")
                     (tu/species->ref)
                     (select-keys [:gene/cgc-name :gene/species]))
          sample-data (assoc sample
                             :gene/id gid
                             :gene/cgc-name (tu/cgc-name-for-sample sample*))]
      (tu/with-gene-fixtures
        [sample-data]
        (fn do-update [conn]
          (let [new-cgc-name (tu/cgc-name-for-sample (wnu/unqualify-keys sample-data "gene"))
                payload {:data {:cgc-name new-cgc-name} :prov nil}
                response (update-gene gid payload)]
            (t/is (ru-hp/ok? response))
            (let [gid-2 (some-> response :body :updated :id)
                  updated (-> response :body :updated)]
              (t/is (= gid gid-2) (pr-str updated))
              (t/is (= (:species updated)
                       (-> sample :gene/species second)))
              (t/is (= (:cgc-name updated) new-cgc-name))
              (tu/query-provenance conn gid-2 :event/update-gene))))))))

(t/deftest update-name-of-dead-gene
  (t/testing "Can still update names of dead genes (provided they remain unique in the system."
    (let [gid (first (gen/sample gsg/id 1))
          sample* (first (tu/gen-sample gsg/uncloned 1))
          sample (-> sample*
                     (wnu/qualify-keys "gene")
                     (tu/species->ref)
                     (select-keys [:gene/cgc-name :gene/species]))
          species (tu/species-ref->latin-name sample)
          init-cgc-name (tu/cgc-name-for-sample sample*)
          sample-data (assoc sample
                             :gene/id gid
                             :gene/biotype :biotype/cds
                             :gene/status :gene.status/dead
                             :gene/cgc-name init-cgc-name)]
      (tu/with-gene-fixtures
        [sample-data]
        (fn do-update [_]
          (let [new-cgc-name (tu/cgc-name-for-species species)
                payload {:data (-> sample-data
                                   (dissoc :gene/status)
                                   (wnu/unqualify-keys "gene")
                                   (update :biotype name)
                                   (assoc :species species)
                                   (dissoc :id)
                                   (assoc :cgc-name new-cgc-name))
                         :prov nil}
                response (update-gene gid payload)]
            (t/is (ru-hp/ok? response))
            (let [updated (-> response :body :updated)]
              (t/is (= (:cgc-name updated) new-cgc-name)
                    (format "EXP:%s!= ACT: %s - INTITIAL: %s"
                            new-cgc-name
                            (:cgc-name updated)
                            init-cgc-name)))))))))

(t/deftest removing-cgc-name-from-uncloned-gene
  (t/testing "Attempts to remove the CGC name from an uncloned gene is not allowed."
    (let [gid (first (gen/sample gsg/id 1))
          sample* (first (tu/gen-sample gsg/uncloned 1))
          sample (-> sample*
                     (wnu/qualify-keys "gene")
                     (tu/species->ref)
                     (select-keys [:gene/cgc-name :gene/species]))
          species (tu/species-ref->latin-name sample)
          sample-data (assoc sample
                             :gene/id gid
                             :gene/biotype :biotype/cds
                             :gene/cgc-name (tu/cgc-name-for-sample sample*))]
      (tu/with-gene-fixtures
        [sample-data]
        (fn do-update [_]
          (let [payload {:data (-> sample-data
                                   (wnu/unqualify-keys "gene")
                                   (update :biotype name)
                                   (assoc :species species)
                                   (dissoc :id)
                                   (assoc :cgc-name nil))
                         :prov nil}
                response (update-gene gid payload)]
            (t/is (ru-hp/bad-request? response))))))))

(t/deftest removing-cgc-name-from-cloned-gene
  (t/testing "Allow CGC name to be removed from a cloned gene."
    (let [gid (first (gen/sample gsg/id 1))
          sample* (first (tu/gen-sample gsg/cloned 1))
          sample (-> sample*
                     (wnu/qualify-keys "gene")
                     (tu/species->ref)
                     (select-keys [:gene/cgc-name :gene/species]))
          species (tu/species-ref->latin-name sample)
          sample-data (assoc sample
                             :gene/id gid
                             :gene/biotype :biotype/cds
                             :gene/sequence-name (tu/seq-name-for-sample sample*)
                             :gene/cgc-name (tu/cgc-name-for-sample sample*))]
      (tu/with-gene-fixtures
        [sample-data]
        (fn do-update [conn]
          (let [payload {:data (-> sample-data
                                   (wnu/unqualify-keys "gene")
                                   (update :biotype name)
                                   (assoc :species species)
                                   (dissoc :id)
                                   (assoc :cgc-name nil))
                         :prov nil}
                response (update-gene gid payload)]
            (t/is (ru-hp/ok? response))
            (let [
                  identifier (some-> response :body :updated :id)]
              (t/is (empty? (some-> response :body :cgc-name)))
              (tu/query-provenance conn identifier :event/update-gene))))))))


(t/deftest gene-provenance
  (t/testing "Provenance is recorded for successful transactions"
    (let [gid (first (gen/sample gsg/id 1))
          sample* (first (tu/gen-sample gsg/cloned 1))
          sample (-> sample*
                     (wnu/qualify-keys "gene")
                     (wne/transform-ident-ref-values)
                     (tu/species->ref))
          orig-cgc-name (tu/cgc-name-for-sample sample*)
          sample-data (merge
                       sample
                       {:gene/id gid
                        :gene/cgc-name orig-cgc-name
                        :gene/status :gene.status/live
                        :gene/sequence-name (tu/seq-name-for-sample sample*)})]
      (tu/with-gene-fixtures
        sample-data
        (fn [conn]
          (let [unqual-sample-data (wnu/unqualify-keys sample-data "gene")
                new-cgc-name (tu/cgc-name-for-sample unqual-sample-data)
                why "update prov test"
                species (tu/species-ref->latin-name sample-data)
                payload {:data (-> unqual-sample-data
                                   (update :biotype name)
                                   (update :status name) 
                                   (assoc :species species)
                                   (dissoc :id :gene/status :other-names)
                                   (assoc :cgc-name new-cgc-name))
                         :prov {:why why
                                :who {:email "tester@wormbase.org"}}}
                response (update-gene gid payload)
                db (d/db conn)
                ent (d/entity db [:gene/id gid])]
            (t/is (ru-hp/ok? response))
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
            (t/is (= new-cgc-name (:gene/cgc-name ent)))))))))

(t/deftest update-other-names-gene
  (t/testing "Testing successful addition to / removal from gene's other-names."
    (let [gid (first (gen/sample gsg/id 1))
          sample* (first (tu/gen-sample gsg/uncloned 1))
          sample  (-> sample*
                      (wnu/qualify-keys "gene")
                      (tu/species->ref)
                      (select-keys [:gene/other-names]))
          sample-data (assoc sample
                             :gene/id gid
                             :gene/other-names (gsg/gen-other-names 2))]
      (tu/with-gene-fixtures
        [sample-data]
        (fn [_]
          ; Test successful PUT /gene/*/update-other-names operation
          (let [original-other-names (:gene/other-names sample-data)
                add-names (gsg/gen-other-names 2)
                payload {:data add-names :prov nil}
                response (api-tc/add-other-names "gene" gid payload)]
            (t/is (ru-hp/ok? response))
            ; Compare successful PUT /gene/*/update-other-names operation return value to expected results
            (let [add-result (some-> response :body)
                  expected-add-result (distinct (concat original-other-names add-names))]
              (t/is (= (set add-result) (set expected-add-result)) (pr-str add-result))
              ; Test successful DELETE /gene/*/update-other-names operation
              (let [n (- (count add-result) 1)
                    retract-names (take n (shuffle add-result))
                    payload {:data retract-names :prov nil}
                    response (api-tc/retract-other-names "gene" gid payload)]
                (t/is (ru-hp/ok? response))
                ; Compare successful DELETE /gene/*/update-other-names operation return value to expected results
                (let [retract-result (some-> response :body)
                      expected-retract-result (cset/difference (set add-result) (set retract-names))]
                  (t/is (= (set retract-result) (set expected-retract-result)) (pr-str retract-result))
                  (t/is (= (count retract-result) 1) "Unexpected gene retract other-names result-size"))
                ))))))))

(t/deftest variation-data-must-meet-spec
  (let [identifier (first (gen/sample gsv/id 1))
        sample {:variation/name (first (gen/sample gsv/name 1))
                :variation/status :variation.status/live}
        sample-data (merge sample {:variation/id identifier})]
    (tu/with-installed-generic-entity
      :variation/id
      "WBVar%08d"
      sample-data
      (fn [_]
        (t/testing (str "Updating name for existing variation requires "
                        "correct data structure.")
          (let [payload {:data {} :prov basic-prov}
                response (update-variation identifier payload)]
            (t/is (ru-hp/bad-request? response))))))))

(t/deftest changing-variation-name-success
  (t/testing "Changing the name of an existing variation."
    (let [identifier (first (gen/sample gsv/id 1))
          sample {:variation/name (first (gen/sample gsv/name 1))
                  :variation/status :variation.status/live}
          sample-data (merge sample {:variation/id identifier})]
      (tu/with-fixtures
        sample-data
        (fn [conn]
          (let [data {:name "mynew1"}
                payload {:data data :prov basic-prov}
                response (update-variation identifier payload)]
            (t/is (ru-hp/ok? response))
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
      (tu/with-installed-generic-entity
        :variation/id
        "WBVar&08d"
        samples
        (fn [_]
          (let [data {:name (-> samples first :variation/name)}
                payload {:data data :prov basic-prov}
                response (update-variation subject-identifier payload)]
            (t/is (ru-hp/conflict? response))))))))

(t/deftest cannot-update-latin-name
  (t/testing "Attempting to update a \"latin-name\" for species is not permitted."
    (let [samp-data (map (fn add-id [sample]
                           (assoc sample :id (-> sample
                                                 :latin-name
                                                 wns/latin-name->id)))
                         (gen/sample gss/payload 2))
          samples (map #(wnu/qualify-keys % "species") samp-data)
          dup-name (-> samples first :species/latin-name)
          species-id-name (-> samples second :species/id name)]
      (tu/with-fixtures
        samples
        (fn [_]
          (let [payload {:data (-> samp-data
                                   second
                                   (assoc :latin-name dup-name))
                         :prov basic-prov}
                response (update-species species-id-name payload)]
            (t/is (ru-hp/bad-request? response))))))))

(t/deftest update-species-success
  (t/testing "Update a species that has a unique new name succeeds."
    (let [sample (reduce-kv (fn add-id [m k v]
                              (cond-> m
                                (= k :species/latin-name) (assoc :species/id (wns/latin-name->id v))
                                true (assoc k v)))
                            {}
                            (-> gss/payload
                                (gen/sample 1)
                                (first)
                                (wnu/qualify-keys "species")))
          species-id-name (-> sample :species/id name)]
      (tu/with-fixtures
        sample
        (fn [_]
          (let [payload {:data (-> sample
                                   (dissoc :species/id :species/latin-name)
                                   (wnu/unqualify-keys "species"))
                         :prov basic-prov}
                response (update-species species-id-name payload)]
            (t/is (ru-hp/ok? response))))))))
