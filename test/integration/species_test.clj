(ns integration.species-test
  (:require
   [clojure.string :as str]
   [clojure.test :as t]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.constdata :refer [elegans-ln]]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.names.service :as wns]
   [wormbase.names.util :as wnu]
   [wormbase.specs.species :as wsp]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def new-species (partial api-tc/new "species"))

(def tester2 "tester2@wormbase.org")

(t/deftest must-meet-spec
  (t/testing "Attempting to create species without required fields yield 400"
    (doseq [payload {:data {:email "nobody"}
                     :prov {:id "WBPerson123"}}]
      (let [response (new-species payload)]
        (t/is (ru-hp/bad-request? response))))))

(t/deftest create-species
  (t/testing "Valid species data returns a created response (201)"
    (let [sample #:species{:latin-name "Red lobster"
                           :id "r-lobster"
                           :cgc-name-pattern ".*"
                           :sequence-name-pattern ".*"}
          payload {:data (wnu/unqualify-keys (dissoc sample :id) "species")
                   :prov {:email tester2}}
          response (new-species payload)]
      (t/is (ru-hp/created? response)))))

(def update-speices (partial api-tc/update "species"))

(t/deftest test-update
  (t/testing "Updating pattern for species."
    (let [sample #:species{:latin-name "Yellow bannana"
                           :id :species/y-bannana
                           :cgc-name-pattern ".*"
                           :sequence-name-pattern ".*"}]
      (tu/with-fixtures
        sample
        (fn [_]
          (let [payload {:data {:sequence-name-pattern "^[a-z].+"}
                         :prov {:email tester2}}
                response (update-speices (-> sample :species/id name) payload)]
          (t/is (ru-hp/ok? response))))))))

(def species-summary (partial api-tc/summary "species"))

(t/deftest test-summary
  (t/testing "Attempting to get summary a none existant species yields 404"
    (let [response (species-summary "c-elegans")]
      (t/is (ru-hp/not-found? response))))
  (t/testing "Getting summary for a species existant in the db"
    (tu/with-fixtures
      []
      (fn check-species-summary [_]
        (let [pct-encoded (str/replace elegans-ln " " "%20")
              response (api-tc/summary "species" pct-encoded)
              body (:body response)]
          (t/is (ru-hp/ok? response))
          (t/is (= (:latin-name body) elegans-ln))
          (t/is (= (:id body) "c-elegans")))))))

(defn species-list []
  (api-tc/parsed-response (tu/get* wns/app
                                   "/api/species"
                                   {}
                                   {"content-type" "application/json"
                                    "authorization" "Token FAKED"})))

(t/deftest test-species-listing
  (t/testing "List of species is retrievable."
    (binding [fake-auth/*gapi-verify-token-response* (api-tc/make-auth-payload :current-user tester2)]
      (let [response (species-list)
            species (:body response)]
        (t/is (ru-hp/ok? response))
        (every? (fn [sp]
                  ((juxt :latin-name
                         :id
                         :cgc-name-pattern
                         :sequence-name-pattern) sp))
                species)))))
