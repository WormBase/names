(ns integration.test-add-names
  (:require
   [clojure.edn :as edn]
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.alpha :as s]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as stest]
   [clojure.string :as str]
   [clojure.test :as t]
   [clojure.walk :as walk]
   [datomic.api :as d]
   [java-time :as jt]
   [ring.mock.request :as mock]
   [org.wormbase.db :as own-db]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.fake-auth] ;; for side effect
   [org.wormbase.names.gene :as gene]
   [org.wormbase.names.service :as service]
   [org.wormbase.specs.gene :as owsg]
   [org.wormbase.test-utils :as tu])
  (:import (java.util Date))) ;; TODO: java.time

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

(defn sample-to-txes
  "Convert a sample generated from a spec into a transactable form."  
  [sample]
  (let [biotype (-> sample :gene/biotype :biotype/id)
        species (-> sample :gene/species vec first)
        assoc-if (fn [m k v]
                   (if v
                     (assoc m k v)
                     m))]
    (-> sample
        (dissoc :provenance/who)
        (dissoc :provenance/why)
        (dissoc :provenance/when)
        (dissoc :provenance/how)
        (dissoc :gene/species)
        (dissoc :gene/biotype)
        (assoc :gene/species species)
        (assoc-if :gene/biotype biotype))))

(t/deftest must-meet-spec
  (let [conn (db-testing/fixture-conn)
        test-user-dbid (:db/id
                        (d/entity (d/db conn)
                                  [:user/email "tester@wormbase.org"]))
        gene-id (first (gen/sample (s/gen :gene/id) 1))
        sample (first (gen/sample (s/gen ::owsg/add-name) 1))
        add-name-data (merge sample {:gene/id gene-id})
        xxx (println "GENE TX-FIXTURE TO TEST AGAINST:" add-name-data)
        yyy (println)
        tx-tmp-id "datomic.tx"
        fixture-data (sample-to-txes add-name-data)
        tx-fixtures [fixture-data
                     {:db/id "datomic.tx"
                      :provenance/who test-user-dbid
                      :provenance/why "Adding new name"
                      :provenance/when (jt/to-java-date (jt/instant))}]
        zzz (println "TRANSACTING GENE TX-FIXfTURE:" tx-fixtures)
        tx-res @(d/transact own-db/conn tx-fixtures)]
    (alter-var-root
     (var own-db/db)
     (fn fixtured-db [real-fn]
       (fn [conn]
         (db-testing/speculate tx-fixtures))))
    (t/testing (str "Adding names to existing genes requires "
                    "correct data structure.")
      (let [name-record {}]
        (let [response (add-gene-name gene-id name-record)
              [status body] response]
          (tu/status-is? status
                         400
                         (pr-str response)))))))
