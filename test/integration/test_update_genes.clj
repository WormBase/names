(ns integration.test-update-genes
  (:require
   [cheshire.core :as json]
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
   [datomic.api :as d]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn add-gene-name [gene-id name-records]
  (post* service/app
         (str "/gene/" gene-id)
         (->> name-records
              (assoc {} :add)
              (json-string))))

(defn update-gene-name [gene-id name-records]
  (put* service/app
         (str "/gene/" gene-id)
         (->> name-records
              (assoc {} :update)
              (json-string))))

(t/deftest add-must-meet-spec
  (t/testing (str "Adding names to genes requires "
                  "correct data structure.")
    (let [name-records [{}]
          [status body] (add-gene-name "WBGene00000001" name-records)]
      (status-is? status 400 body))))

(t/deftest update-gene-name-must-meet-spec
  (t/testing (str "Updating genes require "
                  "correct data structure.")
    (let [name-records [{}]
          [status body] (update-gene-name "WBGene00000001" name-records)]
      (status-is? status 400 body))))

