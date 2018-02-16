(ns integration.test-update-names
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [org.wormbase.db :as owdb]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.fake-auth] ;; for side effect
   [org.wormbase.names.service :as service]
   [org.wormbase.specs.gene :as owsg]
   [org.wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn update-gene-name [gene-id name-record]
  (let [uri (str "/gene/" gene-id)
        put (partial tu/raw-put-or-post* service/app uri :put)
        headers {"auth-user" "tester@wormbase.org"
                 "authorization" "Bearer TOKEN_HERE"
                 "user-agent" "wb-ns-script"}
        data (pr-str name-record)
        [status body] (put data nil headers)]
    [status (tu/parse-body body)]))

(t/deftest must-meet-spec
  (let [identifier (first (gen/sample (s/gen :gene/id) 1))
        sample (-> (gen/sample (s/gen ::owsg/update) 1)
                   (first)
                   (assoc :provenance/who "tester@wormbase.org"))
        sample-data (merge sample {:gene/id identifier})]
    (tu/with-fixtures
      sample-data
      (fn [conn]
        (t/testing (str "Updating name for existing gene requires "
                        "correct data structure.")
          (let [data {}]
            (let [response (update-gene-name identifier data)
                  [status body] response]
              (tu/status-is? status 400 (pr-str response))))))
      :why "Updating name")))

(t/deftest provenance
  (t/testing (str "Provenance is recorded for successful transactions")
    (let [identifier (first (gen/sample (s/gen :gene/id) 1))
          sample (first (gen/sample (s/gen ::owsg/update) 1))
          sample-data (merge sample {:gene/id identifier})
          species-id (:species/id sample)
          reason "Updating a cgc-name records provenance"]
      (tu/with-fixtures
        sample-data
        (fn [conn]
          (let [payload (-> sample-data
                            (assoc :gene/cgc-name "cgc-1")
                            (assoc :provenance/who
                                   [:user/email "tester@wormbase.org"]))
                db (owdb/db conn)]
            (let [response (update-gene-name identifier payload)
                  [status body] response
                  ent (d/entity (d/db conn) [:gene/id identifier])]
              (tu/status-is? status 200 (pr-str response))
              (let [act-prov (tu/query-provenence db identifier)]
                (t/is (= (:provenance/how act-prov) :agent/script)
                      (pr-str act-prov))
                (t/is (= (:provenance/why act-prov) reason))
                (t/is (= (:provenance/who act-prov)
                         "tester@wormbase.org"))
                (t/is (not= nil (:provenance/when act-prov))))
              (let [gs (:gene/status ent)]
                (t/is (= :gene.status/live gs)
                      (pr-str (:gene/status ent))))
              (t/is (= "cgc-1" (:gene/cgc-name ent))))))
        :why reason))))
