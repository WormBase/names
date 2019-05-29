(ns integration.test-person
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.fake-auth]
   [wormbase.specs.person :as wsp]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def new-person (partial api-tc/new "person"))

(t/deftest must-meet-spec
  (t/testing "Attempting to create person without required fields yield 400"
    (doseq [payload [{}
                     {:person/email "nobody"}
                     {:person/id "WBPerson123"}]]
      (let [response (new-person payload)]
        (t/is (ru-hp/bad-request? response))))))

(t/deftest create-person
  (t/testing "Security - only admins can create a person."
    (let [current-user "tester2@wormbase.org" ;; user w/out admin role
          sample (-> (tu/person-samples 1)
                     first
                     (assoc :provenance/who {:person/email current-user}))
          response (new-person sample :current-user current-user)]
      (t/is (ru-hp/unauthorized? response))))
  (t/testing "Valid person data returns a created response (201)"
    (let [sample (first (tu/person-samples 1))
          response (new-person sample)]
      (t/is (ru-hp/created? response)))))

(def person-update (partial api-tc/update "person"))

(t/deftest update-person-requires-admin
  (t/testing "Security - updating person requires admin role"
    (let [current-user "tester2@wormbase.org"
          sample (-> (tu/person-samples 1)
                     first
                     (assoc-in [:provenance/who :person/email]
                               "tester2@wormbase.org")
                     (assoc :person/active? true))
          identifier (:person/id sample)
          update-data {:person/roles #{:person.role/sequence-curator}}]
      (tu/with-fixtures
        [sample]
        (fn check-unauthorized [conn]
          (let [response (person-update identifier
                                        update-data
                                        :current-user current-user)]
            (t/is (ru-hp/unauthorized? response))))))))

(t/deftest update-person-name
  (t/testing "Successfully updating a person."
    (let [sample (-> (tu/person-samples 1)
                     first
                     (assoc :person/active? true))
          identifier (:person/id sample)]
      (tu/with-fixtures
        [sample]
        (fn check-update-success [conn]
          (let [response (person-update identifier
                                        (assoc sample
                                               :person/name
                                               "Joe Bloggs"))]
            (t/is (ru-hp/ok? response))
            (let [result (d/pull (d/db conn) '[*] [:person/id identifier])]
              (t/is (= (:person/name result) "Joe Bloggs")))))))))

(def person-summary (partial api-tc/summary "person"))

(defn deactivated-person-sample []
  (-> (tu/person-samples 1)
      first
      (assoc :person/active? false)))

(t/deftest about
  (t/testing "Attempting to get summary a none existant person yields 404"
    (let [response (person-summary "WBPerson0")]
      (t/is (ru-hp/not-found? response))))
  (t/testing "Getting summary for a person existant in the db by email"
    (tu/with-fixtures
      []
      (fn check-person-summary [conn]
        (let [response (person-summary "tester@wormbase.org")
              body (:body response)]
          (t/is (ru-hp/ok? response))
          (t/is (= (:person/email body) "tester@wormbase.org"))
          (t/is (= (:person/id body) "WBPerson007"))))))
  (t/testing "We get 404 for a decactivated person"
    (let [sample (deactivated-person-sample)
          identifier (:person/id sample)
          response (person-summary identifier)]
      (t/is (ru-hp/not-found? response)))))

(def deactivate-person (partial api-tc/delete "person"))

(t/deftest deactivate
  (t/testing "Attempting to deativate an inactive person results in 400"
    (let [sample (deactivated-person-sample)
          identifier (:person/id sample)]
      (tu/with-fixtures
        sample
        (fn check-404 [conn]
          (let [response (deactivate-person identifier)]
            (t/is (ru-hp/not-found? response))))))))
