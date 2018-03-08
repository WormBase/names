(ns integration.test-new-gene
  (:require
   [clojure.test :as t]
   [org.wormbase.fake-auth :as fake-auth]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.names.service :as service]
   [org.wormbase.test-utils :as tu]
   [datomic.api :as d]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn new-name
  [name-record & {:keys [current-user]
                  :or {current-user "tester@wormbase.org"}}]
  (binding [fake-auth/*current-user* current-user]
    (let [data (->> name-record (assoc {} :new) pr-str)
          current-user-token (get fake-auth/tokens current-user)
          [status body]
          (tu/raw-put-or-post*
           service/app
           "/gene/"
           :post
           data
           "application/edn"
           {"authorization" (str "Bearer " current-user-token)})]
      [status (tu/parse-body body)])))

(def not-nil? (complement nil?))

(defn check-db [db gene-id]
  (let [qr (d/q '[:find (count ?e) .
                  :in $ ?gid status
                  :where
                  [?e :gene/id ?gid]
                  [?e :gene/status ?status]]
                db
                gene-id
                :gene.status/live)]
    (t/is (not-nil? qr))))

(t/deftest must-meet-spec
  (t/testing "Incorrectly naming gene reports problems."
    (let [response (new-name {})
          [status body] response]
      (tu/status-is? status 400 body)
      (t/is (contains? (tu/parse-body body) :problems) (pr-str body))))
  (t/testing "Species should always be required when creating gene name."
    (let [[status body] (new-name {:gene/cgc-name "abc-1"})]
      (tu/status-is? status 400 (format "Body: " body)))))

(t/deftest wrong-data-shape
  (t/testing "Non-conformant data should result in HTTP Bad Request 400"
    (let [[status body] (new-name {})]
      (tu/status-is? status 400 (format "Body: " body)))))

(t/deftest naming-uncloned
  (t/testing "Naming one uncloned gene succesfully returns ids"
    (tu/with-fixtures
      []
      (fn new-uncloned [conn]
        (let [[status body] (new-name
                             {:gene/cgc-name "abc-1"
                              :gene/species {:species/id :species/c-elegans}})
              expected-id "WBGene00000001"]
          (tu/status-is? status 201 body)
          (let [db (d/db conn)
                identifier (some-> body :created :gene/id)]
            (t/is (= identifier expected-id))
            (check-db db identifier)
            (tu/query-provenance conn identifier)))))))

(t/deftest naming-with-provenance
  (t/testing "Naming some genes providing provenance."
    (let [name-record {:gene/cgc-name "abc-1"
                       :gene/species {:species/id :species/c-elegans}
                       :provenance/who {:person/email
                                        "tester@wormbase.org"}}
          [status body] (new-name name-record)]
      (tu/status-is? status 201 body)
      (t/is (some-> body :created :gene/id) (pr-str body)))))

