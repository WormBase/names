(ns integration.test-add-names
  (:require
   [cheshire.core :as json]
   [clojure.pprint :refer [pprint]]
   [clojure.spec.alpha :as s]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as stest]
   [clojure.string :as str]
   [clojure.test :as t]
   [clojure.walk :as walk]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.db :as owdb]
   [org.wormbase.names.service :as service]
   [org.wormbase.test-utils :refer [post* put* json-string
                                    status-is?
                                    body-contains?]]
   [datomic.api :as d]
   [org.wormbase.specs.gene :as owsg]
   [clojure.spec.gen.alpha :as gen]
   [org.wormbase.names.gene :as gene]
   [ring.mock.request :as mock]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn add-gene-name [gene-id name-records]
  (put* service/app
        (str "/gene/" gene-id)
        (->> name-records
             (assoc {} :add)
             (json-string))))

(defn resolve-ref [tx-fixture]
  (let [species (:gene/species tx-fixture)
        biotype (if (contains? tx-fixture :gene/biotype)
                  (name (:gene/biotype tx-fixture)))
        dissoc-if (fn [m k]
                    (if (contains? m k)
                      (dissoc m k)
                      m))
        tx-fixture* (-> tx-fixture
                        (dissoc :gene/species)
                        (dissoc-if :gene/biotype)
                        (assoc :gene/species [:species/id species]))]
    (if-let [bt (:gene/biotype tx-fixture)]
      (assoc tx-fixture* :gene/biotype (keyword "gene.biotype" biotype))
      tx-fixture*)))

(def resolve-refs (partial map resolve-ref))

(t/deftest must-meet-spec
  (let [conn (db-testing/fixture-conn)
        tx-tmp-id (d/tempid :db.part/tx)
        sample (gen/sample (s/gen ::owsg/name-update) 1)
        tx-fixtures (concat
                     (resolve-refs sample)
                     [[:db/add tx-tmp-id
                       :provenance/who [:user/email "tester@wormbase.org"]]
                      [:db/add tx-tmp-id :provenance/why "testing"]
                      [:db/add tx-tmp-id :provenance/when (java.util.Date.)]])]
    (t/testing (str "Adding names to existing genes requires "
                    "correct data structure.")
      (let [db (db-testing/speculate tx-fixtures)
            xxx (if (nil? db)
                  (println "DB was nil"))
            name-records [{}]
            gene-id (-> tx-fixtures first :gene/id)
            gene (d/entity db [:gene/id gene-id])
            [status body] (add-gene-name gene-id name-records)]
    (status-is? status 400 body)))))
