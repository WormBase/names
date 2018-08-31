(ns integration.test-resurrect-gene
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [datomic.api :as d]
   [wormbase.api-test-client :as api-tc]
   [wormbase.db-testing :as db-testing]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.gen-specs.gene :as gsg]
   [wormbase.names.service :as service]
   [wormbase.specs.agent :as wsa]
   [wormbase.test-utils :as tu]
   [ring.util.http-response :as http-response]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn- gen-sample-for-resurrect [& {:keys [live?]
                                    :or {live? false}}]
  (let [[sample] (tu/gene-samples 1)
        gene-id (first (gen/sample gsg/id 1))
        species (-> sample :gene/species :species/id)
        prod-seq-name (tu/seq-name-for-species species)
        data-sample (assoc sample
                           :gene/id gene-id
                           :gene/sequence-name (tu/seq-name-for-species species)
                           :gene/cgc-name (tu/cgc-name-for-species species)
                           :gene/status (if live?
                                          :gene.status/live
                                          :gene.status/dead))]
    [gene-id data-sample]))

(defn resurrect-gene
  [gene-id & {:keys [current-user]
              :or {current-user "tester@wormbase.org"}}]
  (api-tc/send-request "gene" :post {}
                       :sub-path (str gene-id "/resurrect")
                       :current-user current-user))

(t/deftest cases
  (t/testing "Resurrecting a dead gene succesfully"
    (let [[gene-id sample] (gen-sample-for-resurrect)]
      (tu/with-gene-fixtures
        sample
        (fn [conn]
          (let [[status body] (resurrect-gene gene-id)]
            (tu/status-is? status (:status (http-response/ok)) body))))))
  (t/testing "Cannot resurrect live gene"
    (let [[gene-id sample] (gen-sample-for-resurrect :live? true)]
      (tu/with-gene-fixtures
        sample
        (fn [conn]
          (let [[status body] (resurrect-gene gene-id)]
            (tu/status-is? status (:status (http-response/precondition-failed))
                           body)))))))

