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
   [wormbase.constdata :refer [basic-prov]]
   [wormbase.db-testing :as db-testing]
   [wormbase.gen-specs.sequence-feature :as gssf]
   [wormbase.names.util :as wnu]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def entity-type "sequence-feature")

(defn send-change-status-request [op data]
  (let [data* (assoc data :batch-size 1)]
    (if (= op :kill)
      (api-tc/send-request "batch/entity" :delete data* :sub-path entity-type)
      (let [sub-path (str entity-type "/" (name op))]
        (api-tc/send-request "batch/entity" :post data* :sub-path sub-path)))))

(defn make-samples [n]
  (let [coll (map (fn [id]
                    {:sequence-feature/id id
                     :sequence-feature/status :sequence-feature.status/live})
                  (gen/sample gssf/id n))]
    ;; When we don't have unique samples, re-try until we do.
    (if (not= n (-> coll distinct count))
      (make-samples n)
      coll)))

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
        (fn [_]
          (let [data [{:id id} {:id id}]
                response (send-change-status-request :kill {:data data :prov basic-prov})]
            (t/is (ru-hp/ok? response))
            (t/is (-> response :body :dead (get :id "") uuid/uuid-string?))))))))

(t/deftest entity-in-db-missing
  (t/testing "When a single ID specified in batch does not exist in db."
    (let [gid (first (gen/sample gssf/id 1))
          response (send-change-status-request :kill {:data [{:id gid}] :prov basic-prov})]
      (t/is (ru-hp/not-found? response))
      (t/is (str/includes? (get-in response [:body :message] "")
                           "Entity not found")))))

(t/deftest entities-missing-across-batch
  (t/testing "When multiple entities referenced in batch are missing from db."
    (let [fixture-candidates (make-samples 4)
          ids (->> fixture-candidates
                   (map #(find % :sequence-feature/id))
                   (map (partial apply assoc {}))
                   (map #(wnu/unqualify-keys % "sequence-feature")))
          fixtures (take 2 fixture-candidates)
          all-ids (map :id ids)
          fixture-ids (map :sequence-feature/id fixtures)
          expected-not-found (set/difference (set all-ids) (set fixture-ids))]
      (tu/with-fixtures
        fixtures
        (fn [_]
          (let [response (send-change-status-request :kill {:data ids :prov basic-prov})]
            (t/is (ru-hp/not-found? response))
            (t/is (some
                   #(str/includes? (-> response :body pr-str) %)
                   expected-not-found))))))))

(t/deftest change-status-succesfully
  (t/testing "When a batch is expected to succeed."
    (let [fixtures (make-samples 3)
          ids (->> fixtures
                   (map (fn [x]
                          {:sequence-feature/id (:sequence-feature/id x)})))]
      (tu/with-sequence-feature-fixtures
        fixtures
        (fn [conn]
          (doseq [[op exp-resp-key] {:kill :dead
                                     :resurrect :live}]
            (let [data (map #(wnu/unqualify-keys % "sequence-feature") ids)
                  response (send-change-status-request op {:data data :prov basic-prov})]
              (t/is (ru-hp/ok? response))
              (t/is (some-> response :body exp-resp-key :id uuid/uuid-string?)
                    (pr-str response))
              (doseq [m ids]
                (let [ent (d/entity (d/db conn) (find m :sequence-feature/id))]
                  (t/is (= (:sequence-feature/status ent)
                           (keyword "sequence-feature.status"
                                    (name exp-resp-key)))))))))))))

