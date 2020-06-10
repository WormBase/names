(ns integration.person-test
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.fake-auth]
   [wormbase.gen-specs.person :as wgsp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.person :as wsp]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def new-person (partial api-tc/new "person"))

(def tester2 "tester2@wormbase.org")

(t/deftest must-meet-spec
  (t/testing "Attempting to create person without required fields yield 400"
    (doseq [payload [{:data {} :prov nil}
                     {:data {:person/email "nobody"} :prov nil}
                     {:data {:person/id "WBPerson123"} :prov nil}]]
      (let [response (new-person payload)]
        (t/is (ru-hp/bad-request? response))))))

(t/deftest create-person
  (t/testing "Creating a person with valid data."
    (let [sample #:person{:email tester2
                          :id (first (gen/sample wgsp/id 1))
                          :name "Person2"}
          payload {:data (wnu/unqualify-keys (dissoc sample :id) "person")
                   :prov nil}
          response (new-person payload)]
      (t/is (ru-hp/created? response)))))

(def person-update (partial api-tc/update "person"))

(t/deftest update-person-name
  (t/testing "Successfully updating a person."
    (let [identifier (first (gen/sample wgsp/id 1))
          sample #:person{:id identifier
                          :active? true
                          :email tester2
                          :name "Tester 2"}]
      (tu/with-fixtures
        [sample]
        (fn check-update-success [conn]
          (let [response (person-update identifier
                                        {:data (-> sample
                                                   (wnu/unqualify-keys "person")
                                                   (assoc :name "Joe Bloggs"))
                                         :prov nil})]
            (t/is (ru-hp/ok? response))
            (let [result (d/pull (d/db conn) '[*] [:person/id identifier])]
              (t/is (= (:person/name result) "Joe Bloggs")))))))))

(def person-summary (partial api-tc/summary "person"))

(t/deftest about
  (t/testing "Attempting to get summary a none existant person yields 404"
    (let [response (person-summary "WBPerson0")]
      (t/is (ru-hp/not-found? response))))
  (t/testing "Getting summary for a person existant in the db by email"
    (tu/with-fixtures
      []
      (fn check-person-summary [_]
        (let [response (person-summary "tester@wormbase.org")
              body (:body response)]
          (t/is (ru-hp/ok? response))
          (t/is (= (:email body) "tester@wormbase.org"))
          (t/is (= (:id body) "WBPerson007"))))))
  (t/testing "We get 404 for a decactivated person"
    (let [sample #:person{:active? false
                          :name "Joe Bloggs"
                          :email "joe@wormbase.org"
                          :id "WBPerson1234"}
          identifier (:person/id sample)
          response (person-summary identifier)]
      (t/is (ru-hp/not-found? response)))))

(def deactivate-person (partial api-tc/delete "person"))

(t/deftest deactivate
  (t/testing "Attempting to deativate an inactive person results in 404"
    (let [sample #:person{:active? false
                          :name "Joe Bloggs"
                          :email "joe@wormbase.org"
                          :id "WBPerson1234"}
          identifier (:person/id sample)]
      (tu/with-fixtures
        sample
        (fn check-404 [_]
          (let [response (deactivate-person identifier)]
            (t/is (ru-hp/not-found? response))))))))
