(ns integration.test-update-names
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [org.wormbase.db :as owdb]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.fake-auth] ;; for side effect
   [org.wormbase.gen-specs.gene :as gsg]
   [org.wormbase.gen-specs.species :as gss]
   [org.wormbase.names.service :as service]
   [org.wormbase.specs.gene :as owsg]
   [org.wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn update-gene-name [gene-id name-record]
  (let [uri (str "/gene/" gene-id)
        put (partial tu/raw-put-or-post* service/app uri :put)
        headers {"authorization" "Token TOKEN_HERE"}
        data (pr-str name-record)
        [status body] (put data nil headers)]
    [status (tu/parse-body body)]))

(t/deftest must-meet-spec
  (let [identifier (first (gen/sample gsg/id 1))
        sample (-> (gen/sample gsg/update 1)
                   (first)
                   (assoc :provenance/who "tester@wormbase.org"))
        sample-data (merge sample {:gene/id identifier})]
    (tu/with-fixtures
      sample-data
      (fn [conn]
        (t/testing (str "Updating name for existing gene requires "
                        "correct data structure.")
          (let [data {}]
            (let [response (update-gene-name identifier data)
                  [status body] response]
              (tu/status-is? status 400 (pr-str response))))))
      :why "Updating name")))

(defn- gen-valid-name-for-sample [sample generator]
  (-> sample :gene/species :species/id generator (gen/sample 1) first))

(defn cgc-name-for-sample [sample]
  (gen-valid-name-for-sample sample gss/cgc-name))

(defn seq-name-for-sample [sample]
  (gen-valid-name-for-sample sample gss/sequence-name))

(defn query-provenance [conn changed-attr]
  (when-let [tx-ids (d/q '[:find [?tx]
                           :in $ ?event ?changed-attr
                           :where
                           [_ ?changed-attr]
                           [?ev :db/ident ?event]
                           [?tx :provenance/what ?ev]]
                         (-> conn d/db d/history)
                         :event/update
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
          orig-cgc-name (seq-name-for-sample sample)
          sample-data (merge
                       sample
                       {:gene/id identifier
                        :gene/cgc-name orig-cgc-name
                        :gene/status :gene.status/live}
                       (when (contains? sample :gene/sequence-name)
                         {:gene/sequence-name (seq-name-for-sample sample)}))
          species-id (:species/id sample-data)
          reason "Updating a cgc-name records provenance"]
      (tu/with-fixtures
        sample-data
        (fn [conn]
          (let [new-cgc-name (cgc-name-for-sample sample-data)
                why "udpate prov test"
                payload (-> sample-data
                            (dissoc :gene/status)
                            (assoc :gene/cgc-name new-cgc-name)
                            (assoc :provenance/who
                                   [:person/email "tester@wormbase.org"])
                            (assoc :provenance/why
                                   why))]
            (let [response (update-gene-name identifier payload)
                  [status body] response
                  db (d/db conn)
                  ent (d/entity db [:gene/id identifier])]
              (tu/status-is? status 200 body)
              (let [provs (query-provenance conn :gene/cgc-name)
                    act-prov (first provs)]
                (t/is (= (-> act-prov :provenance/what :db/ident) :event/update)
                      (pr-str act-prov))
                (t/is (= (-> act-prov :provenance/how :db/ident) :agent/web)
                      (pr-str act-prov))
                (t/is (= (:provenance/why act-prov) why))
                (t/is (= (-> act-prov :provenance/who :person/email))
                      "tester@wormbase.org")
                (t/is (not= nil (:provenance/when act-prov))))
              (let [gs (:gene/status ent)]
                (t/is (= :gene.status/live gs)
                      (pr-str (:gene/status ent))))
              (t/is (not= orig-cgc-name (:gene/cgc-name ent)))
              (t/is (= new-cgc-name (:gene/cgc-name ent))))))
        :why reason))))
