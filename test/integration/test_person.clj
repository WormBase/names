(ns integration.test-person
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :as t]
   [datomic.api :as d]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.specs.person :as wsp]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def new-person (partial api-tc/new "person"))

(t/deftest must-meet-spec
  (t/testing "Attempting to create person without required fields yield 400"
    (doseq [payload [{}
                     {:person/email "nobody"}
                     {:person/id "WBPerson123"}]]
      (let [[status body] (new-person payload)]
        (tu/status-is? 400 status body)))))

(t/deftest create-person
  (t/testing "Security - only admins can create a person."
    (let [current-user "tester2@wormbase.org" ;; user w/out admin role
          sample (-> (tu/person-samples 1)
                     first
                     (assoc :provenance/who {:person/email current-user}))
          [status body] (new-person sample :current-user current-user)]
      (tu/status-is? 401 status body)))
  (t/testing "Valid person data returns a created response (201)"
    (let [sample (first (tu/person-samples 1))
          [status body] (new-person sample)]
      (tu/status-is? 201 status body))))

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
          (let [[status body] (person-update identifier
                                             update-data
                                             :current-user current-user)]
            (tu/status-is? 401 status body)))))))

(t/deftest update-person-name
  (t/testing "Successfully updating a person."
    (let [sample (-> (tu/person-samples 1)
                     first
                     (assoc :person/active? true))
          identifier (:person/id sample)]
      (tu/with-fixtures
        [sample]
        (fn check-update-success [conn]
          (let [[status body] (person-update identifier
                                             (assoc sample
                                                    :person/name
                                                    "Joe Bloggs"))]
            (tu/status-is? 200 status body)
            (let [result (d/pull (d/db conn) '[*] [:person/id identifier])]
              (t/is (= (:person/name result) "Joe Bloggs")))))))))

(def person-info (partial api-tc/info "person"))

(defn deactivated-person-sample []
  (-> (tu/person-samples 1)
      first
      (assoc :person/active? false)))

(t/deftest about
  (t/testing "Attempting to get info a none existant person yields 404"
    (let [[status body] (person-info "WBPerson0")]
      (tu/status-is? 404 status body)))
  (t/testing "Getting info for a person existant in the db by email"
    (tu/with-fixtures
      []
      (fn check-person-info [conn]
        (let [[status body] (person-info "tester@wormbase.org")]
          (tu/status-is? 200 status body)
          (t/is (= (:person/email body) "tester@wormbase.org"))
          (t/is (= (:person/id body) "WBPerson007"))))))
  (t/testing "We get 404 for a decactivated person"
    (let [sample (deactivated-person-sample)
          identifier (:person/id sample)
          [status body] (person-info identifier)]
      (tu/status-is? 404 status body))))

(def deactivate-person (partial api-tc/delete "person"))

(t/deftest deactivate
  (t/testing "Attempting to deativate an inactive person results in 400"
    (let [sample (deactivated-person-sample)
          identifier (:person/id sample)]
      (tu/with-fixtures
        sample
        (fn check-404-on-about [conn]
          (let [[status body] (deactivate-person identifier)]
            (tu/status-is? 200 status body)))))))
