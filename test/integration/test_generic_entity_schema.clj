(ns integration.test-new-generic-entity
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

(defn new-entity-type [data]
  (api-tc/send-request nil :post data :uri "/api"))

(defn list-entity-types []
  (api-tc/send-request nil :get {} :uri "/api"))

(defn disable-entity-type [ent-type-id]
  (api-tc/send-request nil :delete {} :uri (str "/api/" ent-type-id)))

(t/deftest test-add-new-schema
  (t/testing "Adding a new 'entity type' dynamically to the system"
    (tu/with-fixtures
      [] ;; empty fixtures (re-using mechanism to test database contents)
      (fn [conn]
        (let [data {:entity-type "strain"
                    :id-template "WBStrain%0d"}
              response (new-entity-type {:data data :prov nil})]
          (t/is (ru-hp/created? response))
          (let [db-after (d/db conn)
                pull-ident (partial d/pull db-after '[*])]
            (doseq [attr [:strain/id :strain/status]]
              (t/is (pull-ident attr)
                    (format "Expected attribute %s not in db" attr))
              (doseq [st-attr ["live" "dead" "suppressed"]]
                (t/is (pull-ident (keyword (-> data :entity-type (str ".status")) st-attr)))))))))))

(t/deftest test-list-schema-entities
  (t/testing "We can list the 'entity types' stored in the system."
    (let [schemas (wne/generic-attrs :orange/id "WBOrange%01d")]
      (tu/with-fixtures
        schemas
        (fn [conn]
          (let [response (list-entity-types)
                body (:body response)]
            (t/is (ru-hp/ok? response))
            (let [ent-type-info (-> :entity-types body)
                  ent-types (into #{} (map :entity-type ent-type-info))]
              (t/is (not-empty ent-type-info) (pr-str body))
              (t/is (ent-types "orange")))))))))

(t/deftest test-disable-schema-entity
  (t/testing "Attempt to disable entity type that does not exist."
    (let [response (disable-entity-type "plum")
          body (:body response)]
      (t/is (ru-hp/not-found? response))))
  (t/testing "We can mark a schema entity as disabled."
    (let [schemas (wne/generic-attrs :apple/id "WBApple%01d")]
      (tu/with-fixtures
        schemas
        (fn [conn]
          (let [response (disable-entity-type "apple")
                body (:body response)]
            (t/is (ru-hp/ok? response)))))))
  (t/testing "Disabling an already disabled schema entity"
    (let [schemas (wne/generic-attrs :apple/id "WBApple%01d")]
      (tu/with-fixtures
        schemas
        (fn [conn]
          @(d/transact conn [[:db/add :apple/id :wormbase.names/entity-type-enabled? false]])
          (let [response (disable-entity-type "apple")
                body (:body response)]
            (t/is (ru-hp/conflict? response))))))))
