(ns integration.test-split-gene
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.constdata :refer [elegans-ln]]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.gen-specs.gene :as gs]
   [wormbase.test-utils :as tu]
   [wormbase.db-testing :as db-testing]
   [wormbase.names.service :as service]
   [wormbase.db :as wdb]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn make-auth-payload
  [& {:keys [current-user]
      :or {current-user "tester@wormbase.org"}}]
  (fake-auth/payload {"email" current-user}))

(defn query-tx [conn attr gene]
  (d/q '[:find ?tx .
         :in $ ?attr ?gene
         :where
         [?gene ?attr _ ?tx]]
       (-> conn d/db d/history)
       attr
       gene))

(defn query-provenance [conn attr gene]
  (let [mtx (query-tx conn attr gene)]
    (when mtx
      (d/pull (d/db conn)
              '[:provenance/why
                :provenance/when
                {:provenance/how [:db/ident]
                 :provenance/who [:person/email]}]
              mtx))))

(defn split-gene
  [payload gene-id
   & {:keys [current-user]}]
  (binding [fake-auth/*gapi-verify-token-response* (make-auth-payload
                                                    :current-user
                                                    current-user)]
    (let [data (tu/->json payload)
          [status body] (tu/raw-put-or-post*
                         service/app
                         (str "/api/gene/" gene-id "/split/")
                         :post
                         data
                         "application/json"
                         {"authorization" (str "Token " "FAKED")})]
      [status (tu/parse-body body)])))

(defn undo-split-gene
  [from-id into-id & {:keys [payload current-user]
                      :or {payload {:prov {}}}}]
  (binding [fake-auth/*gapi-verify-token-response* (make-auth-payload
                                                    :current-user
                                                    current-user)]
    (tu/delete service/app
               (str "/api/gene/" from-id "/split/" into-id)
               "application/json"
               (tu/->json payload)
               {"authorization" (str "Token " "FAKED")})))

(t/deftest must-meet-spec
  (t/testing "Target biotype and product must be specified."
    (let [[status body] (split-gene {:data {:gene/cgc-name "abc-1"}
                                     :prov nil}
                                    "WB1")]
      (t/is (ru-hp/bad-request? {:status status :body body}))))
  (t/testing "Request to split gene must meet spec."
    (let [[data-sample] (tu/gene-samples 1)]
      (tu/with-gene-fixtures
        data-sample
        (fn check-validation-error [conn]
          (let [[status body] (split-gene
                               {:data {:gene/biotype :biotype/godzilla
                                       :product {}}
                                :prov nil}
                               (:gene/id data-sample))]
            (t/is (ru-hp/bad-request? {:status status :body body}))
            (t/is (contains? (tu/parse-body body) :problems)
                  (pr-str body))))))))

(t/deftest response-codes
  (t/testing (str "Get 400 response if biotypes "
                  "of both gene and split product not supplied.")
    (let [[status body] (split-gene
                         {:data {:product
                                 {:gene/sequence-name "ABC.1"
                                  :gene/biotype "transcript"}}
                          :prov nil}
                         "WBGene00000001")]
      (t/is (ru-hp/bad-request? {:status status :body body}))
      (t/is (re-matches #".*validation failed.*" (:message body))
            (pr-str body))))
  (t/testing "Get 400 response for product must be specified"
    (let [[status body] (split-gene
                         {:data {:gene/biotype :biotype/transcript}
                          :prov nil}
                         "WBGene00000001")]
      (t/is (ru-hp/bad-request? {:status status :body body}))
      (t/is (re-matches #".*validation failed.*" (:message body)))))
  (t/testing "Get 400 if product biotype not supplied"
    (let [[status body] (split-gene
                         {:data {:product
                                 {:gene/sequence-name "ABC.1"
                                  :gene/biotype "transcript"}}
                          :prov nil}
                         "WBGene00000001")]
      (t/is (ru-hp/bad-request? {:status status :body body}))
      (t/is (re-matches #".*validation failed.*" (:message body))
            (pr-str body))))
  (t/testing "Get 400 if sequence-name not supplied"
    (let [[status body] (split-gene
                         {:data {:gene/biotype :biotype/cds
                                 :product {:gene/biotype :biotype/transposable-element-gene}}
                          :prov nil}
                         "WBGene00000001")]
      (t/is (ru-hp/bad-request? {:status status :body body}))
      (t/is (re-matches #".*validation failed.*" (:message body))
            (pr-str body))))
  (t/testing "Get 404 when gene to be operated on is missing"
    (let [gene-id (first (gen/sample gs/id 1))
          [status body] (split-gene
                         {:data {:gene/biotype :biotype/cds
                                 :product
                                 {:gene/biotype :biotype/cds
                                  :gene/sequence-name "FKM.1"}}
                          :prov nil}
                         gene-id)]
      (t/is (ru-hp/not-found? {:status status :body body}))))
  (t/testing "Expect a conflict response when attempting to split a dead gene."
    (let [[data-sample] (tu/gene-samples 1)
          gene-id (:gene/id data-sample)
          sample (-> data-sample
                     (assoc :gene/biotype :biotype/cds)
                     (assoc :gene/status :gene.status/dead)
                     (assoc :gene/id gene-id))]
      (tu/with-gene-fixtures
        sample
        (fn check-conflict-gene-to-be-split-not-live [conn]
          (assert (d/entity (d/db conn) [:gene/id gene-id])
                  (str "Sample Gene with id: " gene-id " not in db"))
          (let [seq-name (tu/seq-name-for-species
                          (-> sample :gene/species second))
                payload {:data {:gene/biotype :biotype/cds
                                :product
                                {:gene/biotype :biotype/transcript
                                 :gene/sequence-name seq-name}}
                         :prov nil}
                [status body] (split-gene payload gene-id)]
            (t/is (ru-hp/conflict? {:status status :body body})))))))
  (t/testing "400 for validation errors"
    (let [[status body] (split-gene {:data {:gene/biotype :biotype/godzilla}
                                     :prov nil}
                                    "WBGene00000001")]
      (t/is (ru-hp/bad-request? {:status status :body body}))
      (t/is (re-seq #".*validation failed" (:message body))
            (pr-str body)))))

(defn gen-sample-for-split [& {:keys [status]
                                :or {status :gene.status/live}}]
  (let [[sample] (tu/gene-samples 1)
        gene-id (:gene/id sample)
        species (-> sample :gene/species second)
        prod-seq-name (tu/seq-name-for-species species)]
    [gene-id
     (-> sample
         (assoc :gene/status status)
         (assoc :gene/id gene-id)
         (dissoc :gene/cgc-name))
     prod-seq-name]))

(t/deftest success
  (t/testing "Provenence for successful split is recorded."
    (let [[gene-id data-sample prod-seq-name] (gen-sample-for-split)]
      (tu/with-gene-fixtures
        data-sample
        (fn check-provenance [conn]
          (let [db (d/db conn)
                user-email "tester@wormbase.org"
                data {:product {:gene/biotype :biotype/transposable-element-gene
                                :gene/sequence-name prod-seq-name}
                      :gene/biotype :biotype/cds}
                prov {:provenance/why "testing"
                      :provenance/who {:person/email user-email}}
                [status body] (split-gene {:data data :prov prov}
                                          gene-id
                                          :current-user user-email)]
            (t/is (ru-hp/created? {:status status :body body}))
            (let [[from-lur into-lur] [[:gene/id gene-id] [:gene/sequence-name prod-seq-name]]
                  src (d/pull (d/db conn) '[* {:gene/status [:db/ident]
                                               :gene/splits [[:gene/id]]}] from-lur)
                  src-id (:gene/id src)
                  prod (d/pull (d/db conn) '[* {:gene/status [:db/ident]
                                                :gene/_splits [[:gene/id]]}] into-lur)
                  prod-id (:gene/id prod)
                  prov (query-provenance conn :gene/splits (:db/id src))]
              (t/is (= (get-in src [:gene/status :db/ident]) :gene.status/live) "source is not live")
              (t/is (= (get-in prod [:gene/status :db/ident]) :gene.status/live) "product is not live")
              (t/is ((set (map :gene/id (:gene/splits src))) prod-id))
              (t/is ((set (map :gene/id (:gene/_splits prod))) src-id))
              (t/is (some-> prov :provenance/when inst?))
              (t/is (= user-email (some-> prov :provenance/who :person/email)))
              (t/is (= (:gene/species prod) (:gene/species src)))
              (t/is (= :agent/web (some-> prov :provenance/how :db/ident))))))))))

(t/deftest undo-split
  (t/testing "Undo a split operation."
    (let [species elegans-ln
          split-from "WBGene00000001"
          split-into "WBGene00000002"
          from-seq-name (tu/seq-name-for-species species)
          into-seq-name (tu/seq-name-for-species species)
          init-from-gene {:db/id "from"
                          :gene/id split-from
                          :gene/species [:species/latin-name species]
                          :gene/sequence-name from-seq-name
                          :gene/cgc-name "ABC.1"
                          :gene/status :gene.status/live
                          :gene/biotype :biotype/transcript}
          from-gene {:gene/id split-from :gene/splits "into"}
          into-gene {:db/id "into"
                     :gene/id split-into
                     :gene/species [:species/latin-name species]
                     :gene/sequence-name into-seq-name
                     :gene/cgc-name "ABC.2"
                     :gene/status :gene.status/live
                     :gene/biotype :biotype/transcript}
          split-txes [from-gene
                      into-gene
                      {:db/id "datomic.tx"
                       :provenance/why "2 genes for undo split test"
                       :provenance/how :agent/console}]
          conn (db-testing/fixture-conn)]
      (with-redefs [wdb/connection (fn get-fixture-conn [] conn)
                    wdb/db (fn get-db [_] (d/db conn))]
        @(d/transact conn [init-from-gene])
        @(d/transact conn split-txes)
        (let [tx (d/q '[:find ?tx .
                        :in $ ?from-lur ?into-lur
                        :where
                        [?from-lur :gene/splits ?into-lur ?tx]
                        [?into-lur :gene/splits ?from-lur ?tx]]
                      (-> conn d/db d/history)
                      [:gene/id split-from]
                      [:gene/id split-into])
              user-email "tester@wormbase.org"
              [status body] (undo-split-gene split-from
                                             split-into
                                             :current-user user-email)]
          (t/is (ru-hp/ok? {:status status :body body}))
          (let [db (d/db conn)
                invoke (partial d/invoke db)
                [from-g into-g] (map #(d/pull db '[*
                                                   {:gene/splits [[:gene/id]]
                                                    :gene/status [:db/ident]}]
                                                   [:gene/id %])
                                     [split-from split-into])]
            (t/is (nil? (:gene/splits from-g)))
            (t/is (nil? (:gene/splits into-g)))
            (t/is (= (get-in into-g [:gene/status :db/ident]) :gene.status/dead)
                  (str "Into gene:"
                       (d/q '[:find ?e ?aname ?v ?added
                              :in $ ?gid
                              :where
                              [?e :gene/id ?gid]
                              [?e ?a ?v _ ?added]
                              [?a :db/ident ?aname]]
                            (d/history db)
                            split-into)))
            (t/is (= (get-in from-g [:gene/status :db/ident]) :gene.status/live)
                  (str "Expecting live got dead for from gene" split-from))
            ;; prove we don't "reclaim" the identifier
            (t/is (= (invoke :wormbase.tx-fns/next-identifier
                             db
                             :gene/id
                             "WBGene%08d")
                     "WBGene00000003"))
            (t/is (->> (d/q '[:find ?added
                              :in $ ?into-id
                              :where
                              [?e :gene/id ?into-id ?tx ?added]
                              [?e ?a ?v ?tx ?added]
                              [?a :db/ident ?aname]]
                            (-> conn d/db d/history)
                            split-into)
                       (filter false?)
                       (empty?)))))))))
