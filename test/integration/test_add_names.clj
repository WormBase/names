(ns integration.test-add-names
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as str]
   [clojure.test :as t]
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

(defn add-gene-name [gene-id name-record]
  (let [uri (str "/gene/" gene-id)
        put (partial tu/raw-put-or-post* service/app uri :put)
        headers {"auth-user" "tester@wormbase.org"
                 "authorization" "Bearer TOKEN_HERE"
                 "user-agent" "wb-ns-script"}
        data (edn-write {:add name-record})
        [status body] (put data nil headers)]
    [status (tu/parse-body body)]))

(t/deftest must-meet-spec
  (let [identifier (first (gen/sample (s/gen :gene/id) 1))
        sample (first (gen/sample (s/gen ::owsg/update) 1))
        sample-data (merge sample {:gene/id identifier})
        xxx (prn "SAMPLE?:" sample-data)]
    (tu/with-fixtures
      sample-data
      (fn [data prov]
        (println "Testing with:")
        ;; (prn "Data:" data)
        ;; (prn "Prov:" prov)
        (t/testing (str "Adding names to existing genes requires "
                        "correct data structure.")
          (let [data {}]
            (let [response (add-gene-name identifier data)
                  [status body] response]
              (tu/status-is? status 400 (pr-str response))))))
      :why "Adding name")))

