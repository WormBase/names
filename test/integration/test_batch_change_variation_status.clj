(ns integration.test-batch-change-variation-status
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
   [wormbase.gen-specs.variation :as gsv]
   [wormbase.names.util :as wnu]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn send-change-status-request [op data]
  (let [data* (assoc data :batch-size 1)]
    (if (= op :kill)
      (api-tc/send-request "batch" :delete data* :sub-path "variation")
      (let [sub-path (str "variation/" (name op))]
        (api-tc/send-request "batch" :post data* :sub-path sub-path)))))

(defn make-samples [n]
  (map (fn [id]
         (-> (first (gen/sample gsv/payload 1))
             (merge {:id id
                     :status :variation.status/live})
             (wnu/qualify-keys "variation")))
       (gen/sample gsv/id n)))

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (doseq [op [:kill
                :resurrect]]
      (let [response (send-change-status-request op {:data [] :prov basic-prov})]
        (t/is (ru-hp/bad-request? response))))))

(t/deftest dup-ids
  (t/testing "Duplicate entity ids in payload don't cause an error."
    (let [[s1 s2] (make-samples 2)
          id (:variation/id s1)]
      (tu/with-fixtures
        [s1 s2]
        (fn [conn]
          (let [data [id id]
                response (send-change-status-request :kill {:data data :prov basic-prov})]
            (t/is (ru-hp/ok? response))
            (t/is (-> response :body :dead (get :id "") uuid/uuid-string?))))))))

(t/deftest entity-in-db-missing
  (t/testing "When a single ID specified in batch does not exist in db."
    (let [gid (first (gen/sample gsv/id 1))
          response (send-change-status-request :kill {:data [gid]
                                                           :prov basic-prov})]
      (t/is (ru-hp/conflict? response))
      (t/is (str/includes? (get-in response [:body :message] "") "processing errors occurred"))
      (t/is (some (fn [msg]
                    (and (str/includes? msg "does not exist")
                         (str/includes? msg gid)))
                  (-> response :body :errors))
            (pr-str response)))))

(t/deftest entities-missing-across-batch
  (t/testing "When multiple entities referenced in batch are missing from db."
    (let [fixture-candidates (make-samples 4)
          ids (map :variation/id fixture-candidates)
          fixtures (take 2 fixture-candidates)
          expected-not-found (set/difference (set (map :variation/id fixtures)) (set ids))]
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
          ids (map :variation/id fixtures)]
      (tu/with-fixtures
        fixtures
        (fn [conn]
          (doseq [[to-status exp-resp-key] {:kill :dead
                                            :resurrect :live}]
            (let [response (send-change-status-request to-status {:data ids :prov basic-prov})]
              (t/is (ru-hp/ok? response))
              (t/is (some-> response :body exp-resp-key :id uuid/uuid-string?)
                    (pr-str response))
              (doseq [id ids]
                (let [ent (d/entity (d/db conn) [:variation/id id])]
                  (t/is (= (:variation/status ent)
                           (keyword "variation.status" (name exp-resp-key)))))))))))))
