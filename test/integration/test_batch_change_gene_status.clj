(ns integration.test-batch-change-gene-status
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [clj-uuid :as uuid]
   [datomic.api :as d]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov elegans-ln]]
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

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (doseq [op [:kill :resurrect :suppress]]
      (let [response (send-change-status-request op {:data [] :prov basic-prov})]
        (t/is (ru-hp/bad-request? response))))))

(t/deftest dup-ids
  (t/testing "Duplicate entity ids in payload don't cause an error."
    (let [[g1 g2] (tu/gene-samples 2)
          gid (:gene/id g1)]
      (tu/with-gene-fixtures
        [g1 g2]
        (fn [conn]
          (let [data [gid gid]
                response (send-change-status-request :kill {:data data :prov basic-prov})]
            (t/is (ru-hp/ok? response))
            (t/is (-> response :body :dead (get :id "") uuid/uuid-string?))))))))

(t/deftest entity-in-db-missing
  (t/testing "When a single ID specified in batch does not exist in db."
    (let [gid (first (gen/sample gsg/id 1))
          response (send-change-status-request :kill {:data [gid]
                                                      :prov basic-prov})]
      (t/is (ru-hp/conflict? response))
      (t/is (str/includes?  (get-in response [:body :message] "") "processing errors occurred"))
      (t/is (some (fn [msg]
                    (and (str/includes? msg "does not exist")
                         (str/includes? msg gid)))
                  (get-in response [:body :errors]))))))

(t/deftest entities-missing-across-batch
  (t/testing "When multiple entities referenced in batch are missing from db."
    (let [fixture-candidates (tu/gene-samples 4)
          gids (map :gene/id fixture-candidates)
          fixtures (take 2 fixture-candidates)
          expected-not-found (set/difference (set (map :gene/id fixtures)) (set gids))]
      (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (let [response (send-change-status-request :kill {:data gids :prov basic-prov})]
            (t/is (ru-hp/conflict? response))
            (doseq [enf expected-not-found]
              (t/is (str/includes? (get-in response [:body :message] "") enf)))))))))

(t/deftest change-status-succesfully
  (t/testing "When a batch is expected to succeed."
    (let [fixtures (tu/gene-samples 3)
          gids (map :gene/id fixtures)]
      (tu/with-gene-fixtures
        fixtures
        (fn [conn]
          (doseq [[op exp-resp-key] {:kill :dead
                                     :suppress :suppressed
                                     :resurrect :live}]
            (let [response (send-change-status-request op {:data gids :prov basic-prov})]
              (t/is (ru-hp/ok? response))
              (t/is (some-> response :body exp-resp-key :id uuid/uuid-string?)
                    (pr-str response))
              (doseq [gid gids]
                (let [ent (d/entity (d/db conn) [:gene/id gid])]
                  (t/is (= (:gene/status ent)
                           (keyword "gene.status" (name exp-resp-key)))))))))))))

