(ns integration.test-split-gene
  (:require
   [clojure.test :as t]
   [datomic.api :as d]
   [org.wormbase.fake-auth :as fake-auth]
   [org.wormbase.test-utils :as tu]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.names.service :as service]
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.alpha :as s]))


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
          (let [seq-name (tu/gen-valid-seq-name-for-species
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

(t/deftest provenance-recorded
  (t/testing "Provenence for successful split is recorded."
    (let [[[gene-id] _ [_ sample]] (tu/gene-samples 1)
          species (-> sample :gene/species :species/id)
          prod-seq-name (tu/gen-valid-seq-name-for-species species)
          data-sample (-> sample
                          (assoc :gene/id gene-id)
                          (dissoc :gene/cgc-name))]
      (tu/with-fixtures
        data-sample
        (fn check-provenance [conn]
          (let [db (d/db conn)
                user-email "tester@wormbase.org"
                data {:product {:gene/biotype :biotype/transposon
                                :gene/sequence-name prod-seq-name}
                      :gene/biotype :biotype/cds
                      :provenance/why "testing"
                      :provenance/who {:user/email user-email}}
                response (split-gene data
                                     gene-id
                                     :current-user user-email)
                prov (query-provenance conn gene-id prod-seq-name)
                src (d/entity (d/db conn) [:gene/id gene-id])
                prod (d/entity (d/db conn)
                               [:gene/sequence-name prod-seq-name])]
            (t/is (-> prov :provenance/when inst?))
            (t/is (= (:provenance/split-from prov) gene-id))
            (t/is (= (:provenance/split-into prov) (:gene/id prod)))
            (t/is (= (:provenance/who prov) user-email))
            ;; TODO: this should be dependent on the client used for
            ;;       the request.  at the momment, defaults to
            ;;       web-form.
            (t/is (= (:provenance/how prov) :agent/web-form))))))))

