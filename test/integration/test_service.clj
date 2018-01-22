(ns integration.test-service
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [clojure.test :as t]
   [clojure.walk :as walk]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.names.auth :as own-auth]
   [org.wormbase.names.service :as service]
   [org.wormbase.fake-auth]
   [org.wormbase.test-utils :refer [ring-request]]
   [ring.util.http-response :as http-response]
   [muuntaja.core :as m]))

(t/use-fixtures :once db-testing/db-lifecycle)

(t/deftest fallback-404
  (t/testing "When no routes are matched in request processing"
    (let [response (service/app
                    {:uri "/aliens"
                     :headers {"content-type" "application/edn"
                               "accept" "application/edn"
                               "authorization" "Bearer TOKEN_HERE"}
                     :query-params {}
                     :request-method :get})]
      (t/is (= 404 (:status response)))
      (t/is (str/starts-with?
             (peek (http-response/find-header response "Content-Type"))
             "application/edn")
            (str "Wrong content-type?:" (pr-str (:headers response))))
      (t/is (contains? response :body))
      (let [decode #(m/decode service/muuntaja "application/edn" %)
            response-text (some-> response :body slurp)]
        (t/is (not= nil (:reason (decode response-text)))
              (str response-text))))))
