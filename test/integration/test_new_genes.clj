(ns integration.test-new-genes
  (:require
   [cheshire.core :as json]
   [clojure.spec.alpha :as s]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as stest]
   [clojure.string :as str]
   [clojure.test :as t]
   [clojure.walk :as walk]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.names.service :as service]
   [org.wormbase.test-utils :refer [post* json-string
                                    status-is?
                                    body-contains?]]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn new-gene-names [name-records]
  (post* service/app "/gene/" (->> name-records
                                   (assoc {} :new)
                                   (json-string))))

(t/deftest test-new-gene-names-must-meet-spec
  (t/testing "Naming genes requires user to supply names"
    (let [name-records [{}]
          [status body] (new-gene-names name-records)]
      (status-is? status 400 body)
      (t/is (contains? body :problems))
      (t/testing "spec problens are reported"
        (t/is (= #{:path :pred :val :via :in}
                 (-> body :problems first keys set))))))
  (t/testing "Species should always be required when creating gene names"
    (let [[status body] (new-gene-names [{"gene/cgc-name" "abc-1"}])]
      (status-is? status 400 body))))

(t/deftest test-new-genes-wrong-data-shape
  (t/testing "Non-conformant data should result in HTTP Bad Request 400"
    (let [name-records []
          [status body] (new-gene-names name-records)]
      (status-is? status 400 body))))

(t/deftest test-naming-single-uncloned-gene
  (t/testing "Naming one un-cloned gene succesfully returns ids"
    (let [[status body] (new-gene-names
                             [{"gene/cgc-name" "abc-1"
                               "gene/species" "c-elegans"}])
          expected-id "WBGene00000001"]
      (status-is? status 201 body)
      (let [created (:names-created body)]
        (t/is (= (count created) 1))
        (t/is (= (-> created first :gene/id) expected-id))))))

(t/deftest test-naming-many-uncloned-genes-same-species
  (t/testing "Naming many un-cloned genes succesfully returns ids."
    (let [name-records [{"gene/cgc-name" "abc-1"
                         "gene/species" "c-elegans"}
                        {"gene/cgc-name" "abc-2"
                         "gene/species" "c-elegans"}]
          [status body] (new-gene-names name-records)]
      (status-is? status 201 body)
      (t/is (= (count (:names-created body)) (count name-records))
            (pr-str body)))))
