(ns integration.test-about-person
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [org.wormbase.fake-auth :as fake-auth]
   [org.wormbase.test-utils :as tu]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.names.service :as service]
   [datomic.api :as d]
   [org.wormbase.api-test-client :as api-tc]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def person-info (partial api-tc/info "person"))

(t/deftest about-person
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
          (t/is (= (:person/id body) "WBPerson007")))))))




