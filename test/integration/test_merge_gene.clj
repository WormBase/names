(ns integration.test-merge-gene
  (:require
   [clojure.test :as t]
   [org.wormbase.fake-auth :as fake-auth]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.names.service :as service]
   [org.wormbase.test-utils :refer [raw-put-or-post*
                                    parse-body
                                    status-is?
                                    body-contains?]]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn gene-merge
  [payload src-id target-id
   & {:keys [current-user]
      :or {current-user "tester@wormbase.org"}}]
  (binding [fake-auth/*current-user* current-user]
    (let [data (->> payload (assoc {} :into) pr-str)
          current-user-token (get fake-auth/tokens current-user)
          [status body]
          (raw-put-or-post*
           service/app
           (str "/gene/" src-id "/" target-id)
           :post
           data
           "application/edn"
           {"authorization" (str "Bearer " current-user-token)})]
      [status (parse-body body)])))


;; TODO: testing dbfn first...
;; (t/deftest must-meet-spec
;;   (t/testing "Request to merge genes must meet spec."
;;     (let [response (gene-merge {} "WBGene00000001" "WBGene00000002")
;;           [status body] response]
;;       (status-is? status 400 body)
;;       (t/is (contains? (parse-body body) :problems) (pr-str body))))
;;   (t/testing "Species should always be required when creating gene name."
;;     (let [[status body] (gene-merge {:gene/cgc-name "abc-1"})]
;;       (status-is? status 400 (format "Body: " body)))))
