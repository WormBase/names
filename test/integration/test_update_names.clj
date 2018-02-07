(ns integration.test-update-names
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as str]
   [clojure.test :as t]
   [datomic.api :as d]
   [java-time :as jt]
   [ring.mock.request :as mock]
   [org.wormbase.db :as owdb]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.fake-auth] ;; for side effect
   [org.wormbase.names.gene :as gene]
   [org.wormbase.names.service :as service]
   [org.wormbase.specs.gene :as owsg]
   [org.wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(def edn-write pr-str)

(defn update-gene-name [gene-id name-record]
  (let [uri (str "/gene/" gene-id)
        put (partial tu/raw-put-or-post* service/app uri :put)
        headers {"auth-user" "tester@wormbase.org"
                 "authorization" "Bearer TOKEN_HERE"
                 "user-agent" "wb-ns-script"}
        data (edn-write name-record)
        [status body] (put data nil headers)]
    [status (tu/parse-body body)]))

(t/deftest must-meet-spec
  (let [identifier (first (gen/sample (s/gen :gene/id) 1))
        sample (-> (first (gen/sample (s/gen ::owsg/update) 1))
                   (assoc :provenance/who "tester@wormbase.org"))
        sample-data (merge sample {:gene/id identifier})]
    (tu/with-fixtures
      sample-data
      (fn [conn data prov]
        (t/testing (str "Updating name for existing gene requires "
                        "correct data structure.")
          (let [data {}]
            (let [response (update-gene-name identifier data)
                  [status body] response]
              (tu/status-is? status 400 (pr-str response))))))
      :why "Updating name")))


(defn query-provenence [db gene-id]
  (->> (d/q '[:find [?who ?when ?why ?how]
              :in $ ?gene-id
              :where
              [?e :gene/id ?gene-id ?tx]
              [?tx :provenance/who ?u-id]
              [(get-else $ ?u-id :user/email "nobody") ?who]
              [(get-else $ ?tx :provenance/when :unset) ?when]
              [(get-else $ ?tx :provenance/why "Dunno") ?why]
              [(get-else $ ?tx :provenance/how :who-knows?) ?how-id]
              [?how-id :agent/id ?how]]
            (d/history db)
            gene-id)
       (zipmap [:provenance/who :provenance/when :provenance/why :provenance/how])))

(t/deftest provenance
  (t/testing "Testing provenance is recorded when succesfully updating a name."
    (let [identifier (first (gen/sample (s/gen :gene/id) 1))
          sample (first (gen/sample (s/gen ::owsg/update) 1))
          sample-data (merge sample {:gene/id identifier})
          species-id (:species/id sample)
          reason "Updating a cgc-name records provenance"]
      (tu/with-fixtures
        sample-data
        (fn [conn tx-data prov]
          (let [payload (-> sample-data
                            (assoc :gene/cgc-name "cgc-1")
                            (assoc :provenance/who
                                   [:user/email "tester@wormbase.org"]))
                db (owdb/db conn)]
            (let [response (update-gene-name identifier payload)
                  [status body] response]
              (tu/status-is? status 200 (pr-str response))
              (let [act-prov (query-provenence db identifier)]
                (t/is (= (:provenance/how act-prov) :agent/script)
                      (pr-str act-prov))
                (t/is (= (:provenance/why act-prov) reason))
                (t/is (= (:provenance/who act-prov) "tester@wormbase.org"))
                (t/is (not= nil (:provenance/when act-prov)))))
            (t/is (= "cgc-1"
                     (:gene/cgc-name (d/entity (owdb/db conn) [:gene/id identifier]))))))
        :why reason))))
