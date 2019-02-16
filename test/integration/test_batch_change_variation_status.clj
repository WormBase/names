(ns integration.test-batch-change-variation-status
  (:require
   [clj-uuid :as uuid]
   [clojure.set :as set]
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as str]
   [clojure.test :as t]
   [datomic.api :as d]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.variation :as gsv]
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
         (merge
          (first (gen/sample gsv/payload 1))
          {:variation/id id
           :variation/status :variation.status/live}))
       (gen/sample gsv/id n)))

(def elegans-ln "Caenorhabditis elegans")

(def basic-prov {:provenance/who {:person/email "tester@wormbase.org"}})

(t/deftest batch-empty
  (t/testing "Empty batches are rejected."
    (doseq [op [:kill
                :resurrect]]
      (let [[status body] (send-change-status-request op {:data [] :prov basic-prov})]
        (t/is (= 400 status))))))

(t/deftest dup-ids
  (t/testing "Duplicate entity ids in payload don't cause an error."
    (let [[s1 s2] (make-samples 2)
          id (:variation/id s1)]
      (tu/with-fixtures
        [s1 s2]
        (fn [conn]
          (let [data [id id]
                [status body] (send-change-status-request :kill {:data data :prov basic-prov})]
            (tu/status-is? 200 status body)
            (t/is (-> body :dead (get :batch/id "") uuid/uuid-string?))))))))

(t/deftest entity-in-db-missing
  (t/testing "When a single ID specified in batch does not exist in db."
    (let [gid (first (gen/sample gsv/id 1))
          [status body] (send-change-status-request :kill {:data [gid]
                                                           :prov basic-prov})]
      (tu/status-is? 409 status body)
      (t/is (str/includes? (get body :message "") "processing errors occurred"))
      (t/is (some (fn [msg]
                    (and (str/includes? msg "does not exist")
                         (str/includes? msg gid)))
                  (:errors body))
            (pr-str body)))))

(t/deftest entities-missing-across-batch
  (t/testing "When multiple entities referenced in batch are missing from db."
    (let [fixture-candidates (make-samples 4)
          ids (map :variation/id fixture-candidates)
          fixtures (take 2 fixture-candidates)
          expected-not-found (set/difference (set (map :variation/id fixtures)) (set ids))]
      (tu/with-fixtures
        fixtures
        (fn [conn]
          (let [[status body] (send-change-status-request :kill {:data ids :prov basic-prov})]
            (tu/status-is? 409 status body)
            (doseq [enf expected-not-found]
              (t/is (str/includes? (get body :message "") enf)))))))))

(t/deftest change-status-succesfully
  (t/testing "When a batch is expected to succeed."
    (let [fixtures (make-samples 3)
          ids (map :variation/id fixtures)]
      (tu/with-fixtures
        fixtures
        (fn [conn]
          (doseq [[to-status exp-resp-key] {:kill :dead
                                            :resurrect :live}]
            (let [[status body] (send-change-status-request to-status {:data ids :prov basic-prov})]
              (tu/status-is? 200 status body)
              (t/is (some-> body exp-resp-key :batch/id uuid/uuid-string?)
                    (pr-str body))
              (doseq [id ids]
                (let [ent (d/entity (d/db conn) [:variation/id id])]
                  (t/is (= (:variation/status ent)
                           (keyword "variation.status" (name exp-resp-key)))))))))))))
