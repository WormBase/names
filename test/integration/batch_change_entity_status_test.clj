(ns integration.batch-change-entity-status-test
  (:require
   [clj-uuid :as uuid]
   [clojure.set :as set]
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as str]
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.constdata :refer [basic-prov elegans-ln]]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.sequence-feature :as gssf]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def entity-type "sequence-feature")

(defn send-change-status-request [op data]
  (let [data* (assoc data :batch-size 1)]
    (if (= op :kill)
      (api-tc/send-request "batch/generic" :delete data* :sub-path entity-type)
      (let [sub-path (str entity-type "/" (name op))]
        (api-tc/send-request "batch/generic" :post data* :sub-path sub-path)))))

(defn make-samples [n]
  (map (fn [id]
         {:sequence-feature/id id
          :sequence-feature/status :sequence-feature.status/live})
       (gen/sample gssf/id n)))

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (doseq [op [:kill :resurrect]]
      (let [response (send-change-status-request op {:data [] :prov basic-prov})]
        (t/is (ru-hp/bad-request? response))))))

(t/deftest dup-ids
  (t/testing "Duplicate entity ids in payload don't cause an error."
    (let [[s1 s2] (make-samples 2)
          id (:sequence-feature/id s1)]
      (tu/with-fixtures
        [s1 s2]
        (fn [conn]
          (let [data [id id]
                response (send-change-status-request :kill {:data data :prov basic-prov})]
            (t/is (ru-hp/ok? response))
            (t/is (-> response :body :dead (get :id "") uuid/uuid-string?))))))))

(t/deftest entity-in-db-missing
  (t/testing "When a single ID specified in batch does not exist in db."
    (let [gid (first (gen/sample gssf/id 1))
          response (send-change-status-request :kill {:data [gid] :prov basic-prov})]
      (t/is (ru-hp/conflict? response))
      (t/is (str/includes? (get-in response [:body :message] "")
                           "processing errors occurred"))
      (t/is (some (fn [msg]
                    (and (str/includes? msg "does not exist")
                         (str/includes? msg gid)))
                  (-> response :body :errors))
            (pr-str response)))))

(t/deftest entities-missing-across-batch
  (t/testing "When multiple entities referenced in batch are missing from db."
    (let [fixture-candidates (make-samples 4)
          ids (map :sequence-feature/id fixture-candidates)
          fixtures (take 2 fixture-candidates)
          expected-not-found (set/difference (set (map :sequence-feature/id fixtures))
                                             (set ids))]
      (tu/with-fixtures
        fixtures
        (fn [conn]
          (let [response (send-change-status-request :kill {:data ids :prov basic-prov})]
            (t/is (ru-hp/conflict? response))
            (doseq [enf expected-not-found]
              (t/is (str/includes? (get-in response [:body :message] "") enf)))))))))

(t/deftest change-status-succesfully
  (t/testing "When a batch is expected to succeed."
    (let [fixtures (make-samples 3)
          ids (map :sequence-feature/id fixtures)]
      (tu/with-sequence-feature-fixtures
        fixtures
        (fn [conn]
          (doseq [[op exp-resp-key] {:kill :dead
                                     :resurrect :live}]
            (let [response (send-change-status-request op {:data ids :prov basic-prov})]
              (t/is (ru-hp/ok? response))
              (t/is (some-> response :body exp-resp-key :id uuid/uuid-string?)
                    (pr-str response))
              (doseq [id ids]
                (let [ent (d/entity (d/db conn) [:sequence-feature/id id])]
                  (t/is (= (:sequence-feature/status ent)
                           (keyword "sequence-feature.status"
                                    (name exp-resp-key)))))))))))))

