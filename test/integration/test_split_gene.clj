(ns integration.test-split-gene
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.gen-specs.gene :as gs]
   [wormbase.test-utils :as tu]
   [wormbase.db-testing :as db-testing]
   [wormbase.names.service :as service]
   [wormbase.db :as wdb]
   [ring.util.http-response :refer [conflict created not-found]]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn make-auth-payload
  [& {:keys [current-user]
      :or {current-user "tester@wormbase.org"}}]
  (fake-auth/payload {"email" current-user}))

(defn query-provenance [conn attr gene]
  (when-let [mtx (d/q '[:find ?tx .
                        :in $ ?attr ?gene
                        :where
                        [?tx ?attr ?gene]]
                      (-> conn d/db d/history)
                      attr
                      gene)]
    (d/pull (d/db conn)
            '[:provenance/why
              :provenance/when
              {:provenance/split-into [:gene/id]
               :provenance/split-from [:gene/id]
               :provenance/how [:db/ident]
               :provenance/who [:person/email]}]
            mtx)))

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
  [from-id into-id & {:keys [current-user]}]
  (binding [fake-auth/*gapi-verify-token-response* (make-auth-payload
                                                    :current-user
                                                    current-user)]
    (tu/delete service/app
               (str "/api/gene/" from-id "/split/" into-id)
               "application/json"
               {"authorization" (str "Token " "FAKED")})))

(t/deftest must-meet-spec
  (t/testing "Target biotype and product must be specified."
    (let [[status body] (split-gene {:gene/cgc-name "abc-1"} "WB1")]
      (tu/status-is? status 400 (format "Body: " body))))
  (t/testing "Request to split gene must meet spec."
    (let [[data-sample] (tu/gene-samples 1)]
      (tu/with-gene-fixtures
        data-sample
        (fn check-validation-error [conn]
          (let [[status body] (split-gene
                               {:gene/biotype :biotype/godzilla
                                :product {}}
                               (:gene/id data-sample))]
            (tu/status-is? status 400 body)
            (t/is (contains? (tu/parse-body body) :problems)
                  (pr-str body))))))))

(t/deftest response-codes
  (t/testing (str "Get 400 response if biotypes "
                  "of both gene and split product not supplied.")
    (let [[status body] (split-gene
                         {:product
                          {:gene/sequence-name "ABC.1"
                           :gene/biotype "transcript"}}
                         "WBGene00000001")]
      (tu/status-is? status 400 body)
      (t/is (re-matches #".*validation failed.*" (:message body))
            (pr-str body))))
  (t/testing "Get 400 response for product must be specified"
    (let [[status body] (split-gene
                         {:gene/biotype :biotype/transcript}
                         "WBGene00000001")]
      (tu/status-is? status 400 body)
      (t/is (re-matches #".*validation failed.*" (:message body)))))
  (t/testing "Get 400 if product biotype not supplied"
    (let [[status body] (split-gene
                         {:product
                          {:gene/sequence-name "ABC.1"
                           :gene/biotype "transcript"}}
                         "WBGene00000001")]
      (tu/status-is? status 400 body)
      (t/is (re-matches #".*validation failed.*" (:message body))
            (pr-str body))))
  (t/testing "Get 400 if sequence-name not supplied"
    (let [[status body] (split-gene
                         {:gene/biotype :biotype/cds
                          :product
                          {:gene/biotype :biotype/transposable-element-gene}}
                         "WBGene00000001")]
      (tu/status-is? status 400 body)
      (t/is (re-matches #".*validation failed.*" (:message body))
            (pr-str body))))
  (t/testing "Get 404 when gene to be operated on is missing"
    (let [gene-id (first (gen/sample gs/id 1))
          [status body] (split-gene
                         {:gene/biotype :biotype/cds
                          :product
                          {:gene/biotype :biotype/cds
                           :gene/sequence-name "FKM.1"}}
                         gene-id)]
      (tu/status-is? (:status (not-found)) status body)))
  (t/testing "Expect a conflict response when attempting to split a dead gene."
    (let [[data-sample] (tu/gene-samples 1)
          gene-id (:gene/id data-sample)
          sample (-> data-sample
                     (assoc :gene/biotype :biotype/cds)
                     (assoc :gene/status :gene.status/dead)
                     (assoc :gene/id gene-id))]
      (assert (contains? sample :gene/id))
      (tu/with-gene-fixtures
        sample
        (fn check-conflict-gene-to-be-split-not-live [conn]
          (let [seq-name (tu/seq-name-for-species
                          (-> sample :gene/species :species/id))
                [status body] (split-gene
                               {:gene/biotype :biotype/cds
                                :product
                                {:gene/biotype :biotype/transcript
                                 :gene/sequence-name seq-name}}
                               gene-id)]
            (tu/status-is? (:status (conflict)) status body))))))
  (t/testing "400 for validation errors"
    (let [[status body] (split-gene {:gene/biotype :biotype/godzilla}
                                    "WBGene00000001")]
      (tu/status-is? status 400 body)
      (t/is (re-seq #".*validation failed" (:message body))
            (pr-str body)))))

(defn- gen-sample-for-split [& {:keys [status]
                                :or {status :gene.status/live}}]
  (let [[sample] (tu/gene-samples 1)
        gene-id (:gene/id sample)
        species (-> sample :gene/species :species/id)
        prod-seq-name (tu/seq-name-for-species species)]
    [gene-id
     (-> sample
         (assoc :gene/status status)
         (assoc :gene/id gene-id)
         (dissoc :gene/cgc-name))
     prod-seq-name]))

(t/deftest provenance-recorded
  (t/testing "Provenence for successful split is recorded."
    (let [[gene-id data-sample prod-seq-name] (gen-sample-for-split)]
      (tu/with-gene-fixtures
        data-sample
        (fn check-provenance [conn]
          (let [db (d/db conn)
                user-email "tester@wormbase.org"
                data {:product {:gene/biotype :biotype/transposable-element-gene
                                :gene/sequence-name prod-seq-name}
                      :gene/biotype :biotype/cds
                      :provenance/why "testing"
                      :provenance/who {:person/email user-email}}
                [status body] (split-gene data
                                          gene-id
                                          :current-user user-email)]
            (tu/status-is? (:status (created "/")) status body)
            (let [[from-lur into-lur] [[:gene/id gene-id] [:gene/sequence-name prod-seq-name]]
                  src  (d/entity (d/db conn) from-lur)
                  prod (d/entity (d/db conn) into-lur)
                  prov-from (query-provenance conn :provenance/split-from (:db/id src))
                  prov-into (query-provenance conn :provenance/split-into (:db/id prod))]
              (t/is (some-> prov-from :provenance/when inst?))
              (t/is (some-> prov-into :provenance/when inst?))
              (t/is (= gene-id (some-> prov-from :provenance/split-from :gene/id)))
              (t/is (= (:gene/id prod) (some-> prov-into :provenance/split-into :gene/id)))
              (t/is (= user-email (some-> prov-from :provenance/who :person/email)))
              (t/is (= user-email (some-> prov-into :provenance/who :person/email)))
              (t/is (= (:gene/species prod) (:gene/species src)))
              (t/is (= :agent/web (some-> prov-from :provenance/how :db/ident)))
              (t/is (= :agent/web (some-> prov-into :provenance/how :db/ident))))))))))

(t/deftest undo-split
  (t/testing "Undo a split operation."
    (let [species :species/c-elegans
          split-from "WBGene00000001"
          split-into "WBGene00000002"
          from-seq-name (tu/seq-name-for-species species)
          into-seq-name (tu/seq-name-for-species species)
          from-gene {:db/id from-seq-name
                     :gene/id split-from
                     :gene/sequence-name from-seq-name
                     :gene/species [:species/id species]
                     :gene/status :gene.status/live
                     :gene/biotype :biotype/cds}
          into-gene {:db/id into-seq-name
                     :gene/id split-into
                     :gene/species [:species/id species]
                     :gene/sequence-name into-seq-name
                     :gene/cgc-name "ABC.1"
                     :gene/status :gene.status/live
                     :gene/biotype :biotype/transcript}
          init-txes [[from-gene
                       {:db/id "datomic.tx"
                        :provenance/why "A gene in the system"
                        :provenance/how :agent/console}]
                     [into-gene
                      {:db/id "datomic.tx"
                       :provenance/split-from [:gene/sequence-name
                                               from-seq-name]
                       :provenance/split-into into-seq-name
                       :provenance/why
                       "a gene that has been split for testing undo"
                       :provenance/how :agent/console}]]
          conn (db-testing/fixture-conn)]
      (with-redefs [wdb/connection (fn get-fixture-conn [] conn)
                    wdb/db (fn get-db [_] (d/db conn))]
        (doseq [init-tx init-txes]
          @(d/transact-async conn init-tx))
        (let [tx (d/q '[:find ?tx .
                        :in $ ?into-id ?from-lur ?into-lur
                        :where
                        [?tx :provenance/split-from ?from-lur]
                        [?tx :provenance/split-into ?into-lur]
                        [?e :gene/id ?into-id ?tx]]
                      (-> conn d/db d/history)
                      split-into
                      [:gene/id split-from]
                      [:gene/id split-into])
              [ent-id tx-id] (d/q '[:find [?e ?tx]
                                    :in $ ?gid
                                    :where
                                    [?e :gene/id ?gid ?tx]]
                                  (-> conn d/db d/history)
                                  split-into)
              user-email "tester@wormbase.org"
              [status body] (undo-split-gene split-from
                                             split-into
                                             :current-user user-email)]
          (tu/status-is? status 200 body)
          (let [db (d/db conn)
                invoke (partial d/invoke db)
                [from-g into-g] (map #(d/entity db [:gene/id %])
                                     [split-from split-into])]
            (t/is (= (:gene/status from-g) :gene.status/live))
            (t/is (= (:gene/status into-g) :gene.status/dead)
                  (str "Into gene:"
                       (d/q '[:find ?e ?aname ?v ?added
                              :in $ ?gid
                              :where
                              [?e :gene/id ?gid]
                              [?e ?a ?v _ ?added]
                              [?a :db/ident ?aname]]
                            (d/history db)
                            split-into)))
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
