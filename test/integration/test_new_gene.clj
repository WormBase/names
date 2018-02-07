(ns integration.test-new-gene
  (:require
   [cheshire.core :as json]
   [clojure.spec.alpha :as s]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as stest]
   [clojure.string :as str]
   [clojure.test :as t]
   [clojure.walk :as walk]
   [org.wormbase.fake-auth :as fake-auth]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.names.service :as service]
   [org.wormbase.test-utils :refer [raw-put-or-post*
                                    parse-body
                                    status-is?
                                    body-contains?]]
   [datomic.api :as d]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn new-name
  [name-record & {:keys [current-user]
                  :or {current-user "tester@wormbase.org"}}]
  (binding [fake-auth/*current-user* current-user]
    (let [data (->> name-record (assoc {} :new) pr-str)
          current-user-token (get fake-auth/tokens current-user)
          [status body]
          (raw-put-or-post*
           service/app
           "/gene/"
           :post
           data
           "application/edn"
           {"authorization" (str "Bearer " current-user-token)})]
      [status (parse-body body)])))

(defn check-db [gene-id]
  (d/q '[:find ?status
         :where
         [?e :gene/id gene-id]
         [?e :gene/status ?status]
         ]))

(t/deftest must-meet-spec
  (t/testing "Incorrectly naming gene reports problems."
    (let [response (new-name {})
          [status body] response]
      (status-is? status 400 body)
      (t/is (contains? (parse-body body) :problems) (pr-str body))))
  (t/testing "Species should always be required when creating gene name."
    (let [[status body] (new-name {:gene/cgc-name "abc-1"})]
      (status-is? status 400 (format "Body: " body)))))

(t/deftest wrong-data-shape
  (t/testing "Non-conformant data should result in HTTP Bad Request 400"
    (let [[status body] (new-name {})]
      (status-is? status 400 (format "Body: " body)))))

(t/deftest naming-uncloned
  (t/testing "Naming one uncloned gene succesfully returns ids"
    (let [[status body] (new-name
                         {:gene/cgc-name "abc-1"
                          :gene/species {:species/id :species/c-elegans}})
          expected-id "WBGene00000001"]
      (status-is? status 201 body)
      (t/is (= (some-> body :created :gene/id) expected-id)))))

(t/deftest naming-with-provenance
  (t/testing "Naming some genes providing provenance."
    (let [name-record {:gene/cgc-name "abc-1"
                       :gene/species {:species/id :species/c-elegans}
                       :provenance/who {:user/email
                                        "tester@wormbase.org"}}
          [status body] (new-name name-record)]
      (status-is? status 201 body)
      (t/is (some-> body :created :gene/id) (pr-str body)))))

