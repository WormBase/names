(ns integration.test-service
  (:require
   [cheshire.core :as json]
   [clojure.test :as t]
   [clojure.walk :as walk]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.names.service :as service]))

(t/use-fixtures :once db-testing/db-lifecycle)

(t/deftest fallback-404
  (t/testing "When no routes are matched in request processing"
    (let [response (service/app
                    {:uri "/aliens"
                     :headers {:Content-Type "application/json"}
                     :request-method :get})]
      (t/is (= 404 (:status response)))
      (t/is (= "application/json; charset=utf-8"
               (get-in response [:headers "Content-Type"])))
      (t/is (contains? response :body))
      (t/is (not= nil (-> response
                       :body
                       slurp
                       json/parse-string
                       walk/keywordize-keys
                       :reason
                       not-empty))))))
