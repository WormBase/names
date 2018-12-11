(ns integration.test-batch-change-gene-status
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [clj-uuid :as uuid]
   [ring.util.http-response :refer [bad-request conflict not-found ok]]   
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.test-utils :as tu]
   [wormbase.gen-specs.gene :as gsg]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn send-change-status-request [op data]
  (let [data* (assoc data :batch-size 1)]
    (if (= op :kill)
      (api-tc/send-request "batch" :delete data* :sub-path "gene")
      (let [sub-path (str "gene/" (name op))]
        (api-tc/send-request "batch" :post data* :sub-path sub-path)))))

(def elegans-ln "Caenorhabditis elegans")

(def basic-prov {:provenance/who {:person/email "tester@wormbase.org"}})

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (doseq [op [:kill :resurrect :suppress]]
      (let [[status body] (send-change-status-request op {:data [] :prov nil})]
        (t/is (= (:status (bad-request)) status))))))

(t/deftest dup-ids
  (t/testing "Duplicate entity ids in payload don't cause an error."
    (let [[g1 g2] (tu/gene-samples 2)
          gid (:gene/id g1)]
      (tu/with-gene-fixtures
        [g1 g2]
        (fn [conn]
          (let [data [gid gid]
                [status body] (send-change-status-request :kill {:data data :prov nil})]
            (tu/status-is? (:status (ok)) status body)
            (t/is (-> body :dead (get :batch/id "") uuid/uuid-string?))))))))

(t/deftest entity-in-db-missing
  (t/testing "When a single ID specified in batch does not exist in db."
    (let [gid (first (gen/sample gsg/id 1))
          [status body] (send-change-status-request :kill {:data [gid]
                                                           :prov nil})]
      (tu/status-is? (:status (not-found)) status body)
      (t/is (str/includes? (get body :message) (str ":gene/id '" gid "' does not exist"))
            (pr-str body)))))

(t/deftest entities-missing-across-batch
  (t/testing "When multiple entities referenced in batch are missing from db."
    (let [fixture-candidates (tu/gene-samples 4)
          gids (map :gene/id fixture-candidates)
          fixtures (random-sample 0.5 fixture-candidates)
          expected-not-found (set/difference (set gids) (set (map :gene/id fixtures)))]
      (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (let [[status body] (send-change-status-request :kill {:data gids :prov nil})
                entity-missing (some-> body :entity second)]
            (tu/status-is? (:status (not-found)) status body)
            (t/is entity-missing "No entity was determined to be missing.")
            (t/is (str/includes? (get body :message "") entity-missing))))))))

(t/deftest change-status-succesfully
  (t/testing "When a batch is expected to succeed."
    (let [fixtures (tu/gene-samples 3)
          gids (map :gene/id fixtures)]
      (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (doseq [[to-status exp-resp-key] {:kill :dead
                                            :suppress :suppressed
                                            :resurrect :live}]
            (let [[status body] (send-change-status-request to-status {:data gids :prov nil})]
              (tu/status-is? (:status (ok)) status body)
              (t/is (some-> body exp-resp-key :batch/id uuid/uuid-string?)
                    (pr-str body)))))))))

