(ns integration.generic-entity-schema-test
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

(defn send-coll-request [method payload]
  (api-tc/send-request nil
                       method
                       (or payload  {:prov nil :data nil})
                       :uri "/api/generic"))

(defn send-item-request [method payload ent-type-id]
  (api-tc/send-request nil
                       method
                       (or payload  {:prov nil :data {}})
                       :uri (str "/api/generic/" ent-type-id)))

(defn new-entity-type [payload]
  (send-coll-request :post payload))

(defn list-entity-types []
  (send-coll-request :get {}))

(defn disable-entity-type [ent-type-id]
  (send-item-request :delete nil ent-type-id))

(defn enable-entity-type [ent-type-id]
  (send-item-request :put nil ent-type-id))

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
    (tu/with-installed-generic-entity
      :orange/id
      "WBOrange%d"
      (fn [conn]
        (let [response (list-entity-types)
              body (:body response)]
          (t/is (ru-hp/ok? response))
          (let [ent-type-info (:entity-types body)
                ent-types (into #{} (map :entity-type ent-type-info))]
            (t/is (not-empty ent-type-info) (pr-str body))
            (t/is (ent-types "orange"))))))))

(t/deftest test-disable-entity-type
  (let [apple-id-template "WBApple%08d"
        plum-id-template "WBPlum%08d"
        pear-id-template "WBPear%08d"]
    (t/testing "Attempt to disable entity type that does not exist."
      (let [response (disable-entity-type "plum")]
        (t/is (ru-hp/not-found? response))))
    (t/testing "We can mark a schema entity as disabled."
      (tu/with-installed-generic-entity
        :apple/id
        "WBApple%08d"
        (fn [conn]
          (let [response (disable-entity-type "apple")
                body (:body response)]
            (t/is (ru-hp/ok? response))))))
    (t/testing "Disabling an already disabled schema entity is ok."
      (tu/with-installed-generic-entity
        :apple/id
        "WBApple%03d"
        (fn [conn]
          @(d/transact conn [[:db/add :apple/id wne/enabled-ident false]])
          (let [response (disable-entity-type "apple")
                body (:body response)]
            (t/is (ru-hp/ok? response))))))))

(t/deftest test-enable-entity-type-schema
  (t/testing "Doesn't matter if already enabled."
    (tu/with-installed-generic-entity
      :pear/id
      "WBPear%08d"
      (fn [conn]
        (let [response (enable-entity-type "pear")]
          (t/is (ru-hp/ok? response))))))
  (t/testing "Succeed if entity type is disabled."
    (tu/with-installed-generic-entity
      :orange/id
      "WBOrange08d"
      (fn [conn]
        @(d/transact conn
                     [[:db/cas :orange/id :wormbase.names/entity-type-enabled? true false]])
        (let [response (enable-entity-type "orange")]
          (t/is (ru-hp/ok? response)))))))
