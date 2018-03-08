(ns integration.test-split-gene
  (:require
   [clojure.test :as t]
   [datomic.api :as d]
   [org.wormbase.fake-auth :as fake-auth]
   [org.wormbase.test-utils :as tu]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.names.service :as service]
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.alpha :as s]
   [org.wormbase.db :as owdb]))


(t/use-fixtures :each db-testing/db-lifecycle)

(defn- query-provenance [conn from-gene-id into-seq-name]
  (some->> (d/q '[:find [?tx ?from-gid ?into-gid
                         ?how ?when ?who ?why ?status]
                  :in $ ?from ?into
                  :where
                  [?tx :provenance/split-from ?from]
                  [?tx :provenance/split-into ?into]
                  [(get-else $ ?from :gene/id :no-from) ?from-gid]
                  [(get-else $ ?into :gene/id :no-into) ?into-gid]
                  [?into :gene/status ?sid]
                  [?sid :db/ident ?status]
                  [?tx :provenance/why ?why]
                  [?tx :provenance/when ?when]
                  [?tx :provenance/who ?wid]
                  [?wid :user/email ?who]
                  [?tx :provenance/how ?hid]
                  [?hid :agent/id ?how]]
                (-> conn d/db d/history)
                [:gene/id from-gene-id]
                [:gene/sequence-name into-seq-name])
           (zipmap [:provenance/tx
                    :provenance/split-from
                    :provenance/split-into
                    :provenance/how
                    :provenance/when
                    :provenance/who
                    :provenance/why])))

(defn split-gene
  [payload gene-id & {:keys [current-user]
                      :or {current-user "tester@wormbase.org"}}]
  (binding [fake-auth/*current-user* current-user]
    (let [data (pr-str payload)
          current-user-token (get fake-auth/tokens current-user)
          [status body] (tu/raw-put-or-post*
                         service/app
                         (str "/gene/" gene-id "/split/")
                         :post
                         data
                         "application/edn"
                         {"authorization" (str "Bearer "
                                               current-user-token)})]
      [status (tu/parse-body body)])))

(defn undo-split-gene
  [from-id into-id & {:keys [current-user]
                      :or {current-user "tester@wormbase.org"}}]
  (binding [fake-auth/*current-user* current-user]
    (let [current-user-token (get fake-auth/tokens current-user)]
      (tu/delete service/app
                 (str "/gene/" from-id "/split/" into-id)
                 "application/edn"
                 {"authorization" (str "Bearer " current-user-token)}))))

(t/deftest must-meet-spec
  (t/testing "Target biotype and product must be specified."
    (let [[status body] (split-gene {:gene/cgc-name "abc-1"} "WB1")]
      (tu/status-is? status 400 (format "Body: " body))))
  (t/testing "Request to split gene must meet spec."
    (let [[[gene-id] _ [_  data-sample]] (tu/gene-samples 1)]
      (tu/with-fixtures
        data-sample
        (fn check-validation-error [conn]
          (let [[status body] (split-gene
                               {:gene/biotype :biotype/godzilla
                                :product {}}
                               gene-id)]
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
      (t/is (re-matches #"Invalid.*split.*" (:message body))
            (pr-str body))))
  (t/testing "Get 400 response for product must be specified"
    (let [[status body] (split-gene
                         {:gene/biotype :biotype/transcript}
                         "WBGene00000001")]
      (tu/status-is? status 400 body)
      (t/is (re-matches #"Invalid.*split.*" (:message body)))))
  (t/testing "Get 400 if product biotype not supplied"
    (let [[status body] (split-gene 
                         {:product
                          {:gene/sequence-name "ABC.1"
                           :gene/biotype "transcript"}}
                         "WBGene00000001")]
      (tu/status-is? status 400 body)
      (t/is (re-matches #"Invalid.*split.*" (:message body))
            (pr-str body))))
  (t/testing "Get 400 if sequence-name not supplied"
    (let [[status body] (split-gene
                         {:gene/biotype :biotype/cds
                          :product
                          {:gene/biotype :biotype/transposon}}
                         "WBGene00000001")]
      (tu/status-is? status 400 body)
      (t/is (re-matches #"Invalid.*split.*" (:message body))
            (pr-str body))))
  (t/testing "Get 404 when gene to be operated on is missing"
    (let [gene-id (-> :gene/id s/gen (gen/sample 1) first)
          [status body] (split-gene
                         {:gene/biotype :biotype/cds
                          :product
                          {:gene/biotype :biotype/cds
                           :gene/sequence-name "FKM.1"}}
                         gene-id)]
      (tu/status-is? status 404 body)))
  (t/testing "409 for conflicting state"
    (let [[[gene-id] _ [_  data-sample]] (tu/gene-samples 1)
          sample (-> data-sample
                     (assoc :gene/biotype :biotype/cds)
                     (assoc :gene/status :gene.status/dead)
                     (assoc :gene/id gene-id))]
      (assert (contains? sample :gene/id))
      (tu/with-fixtures
        sample
        (fn check-conflict-gene-to-be-split-not-live [conn]
          (let [seq-name (tu/gen-valid-seq-name
                          (-> sample :gene/species :species/id))
                [status body] (split-gene
                               {:gene/biotype :biotype/cds
                                :product
                                {:gene/biotype :biotype/transcript
                                 :gene/sequence-name seq-name}}
                               gene-id)]
            (t/is (= status 409) (pr-str body)))))))

  (t/testing "400 for validation errors"
    (let [[status body] (split-gene {:gene/biotype :biotype/godzilla}
                                    "WBGene00000001")]
      (t/is (= status 400) (pr-str body))
      (t/is (re-seq #"Invalid.*split" (:message body))
            (str "\nBODY:" (pr-str body))))))


(defn- gen-sample-for-split []
  (let [[[gene-id] _ [_ sample]] (tu/gene-samples 1)
        species (-> sample :gene/species :species/id)
        prod-seq-name (tu/gen-valid-seq-name species)]
    [gene-id
     (-> sample
         (assoc :gene/id gene-id)
         (dissoc :gene/cgc-name))
     prod-seq-name]))

(t/deftest provenance-recorded
  (t/testing "Provenence for successful split is recorded."
    (let [[gene-id data-sample prod-seq-name] (gen-sample-for-split)]
      (tu/with-fixtures
        data-sample
        (fn check-provenance [conn]
          (let [db (d/db conn)
                user-email "tester@wormbase.org"
                data {:product {:gene/biotype :biotype/transposon
                                :gene/sequence-name prod-seq-name}
                      :gene/biotype :biotype/cds
                      :provenance/why "testing"
                      :provenance/who {:person/email user-email}}
                [status body] (split-gene data
                                          gene-id
                                          :current-user user-email)
                prov (query-provenance conn gene-id prod-seq-name)
                src (d/entity (d/db conn) [:gene/id gene-id])
                prod (d/entity (d/db conn)
                               [:gene/sequence-name prod-seq-name])]
            (t/is (= status 201) (pr-str body))
            (t/is (some-> prov :provenance/when inst?))
            (t/is (= (some-> prov :provenance/split-from :gene/id)
                     gene-id))
            (t/is (= (some-> prov :provenance/split-into :gene/id)
                     (:gene/id prod)))
            (t/is (= (some-> prov :provenance/who :person/email)
                     user-email))
            (t/is (= (:gene/species src) (:gene/species prod)))
            ;; TODO: this should be dependent on the client used for
            ;;       the request.  at the momment, defaults to
            ;;       web-form.
            (t/is (= (:provenance/how prov) :agent/web-form))))))))

(t/deftest undo-split
  (t/testing "Undo a split operation."
    (let [species :species/c-elegans
          split-from "WBGene00000001"
          split-into "WBGene00000002"
          from-seq-name (tu/gen-valid-seq-name species)
          into-seq-name (tu/gen-valid-seq-name species)
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
                        :provenance/how :agent/script}]
                     [into-gene
                      {:db/id "datomic.tx"
                       :provenance/split-from [:gene/sequence-name
                                               from-seq-name]
                       :provenance/split-into into-seq-name
                       :provenance/why
                       "a gene that has been split for testing undo"
                       :provenance/how :agent/script}]]
          conn (db-testing/fixture-conn)]
      (with-redefs [owdb/connection (fn get-fixture-conn [] conn)
                    owdb/db (fn get-db [_] (d/db conn))]
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
