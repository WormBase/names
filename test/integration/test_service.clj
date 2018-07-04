(ns integration.test-service
  (:require
   [clojure.string :as str]
   [clojure.test :as t]
   [wormbase.db-testing :as db-testing]
   [wormbase.names.service :as service]
   [wormbase.fake-auth]
   [ring.util.http-response :as http-response]))

(t/use-fixtures :once db-testing/db-lifecycle)

(t/deftest fallback-404
  (t/testing "When request uri starts with /api but matches no route"
    (let [response (service/app
                    {:uri "/api/aliens"
                     :headers {"content-type" "application/json"
                               "accept" "application/json"
                               "authorization" "Token TOKEN_HERE"}
                     :query-params {}
                     :request-method :get})]
      (t/is (= 404 (:status response)))
      (t/is (str/starts-with?
             (peek (http-response/find-header response "Content-Type"))
             "application/json")
            (str "Wrong content-type?:" (pr-str (:headers response))))
      (t/is (contains? response :body))
      (let [decode #(service/decode-content "application/json" %)
            response-text (some-> response :body slurp)]
        (t/is (not= nil (:reason (decode response-text)))
              (str response-text)))))
  (t/testing "When no routes are matched in request processing, client api is served."
    (let [response (service/app
                    {:uri "/client/route/like/this"
                     :headers {"content-type" "application/html"
                               "accept" "text/html"}
                     :query-params {}
                     :request-method :get})]
      (t/is (= 200 (:status response)))
      (let [ct (peek (http-response/find-header response "Content-Type"))]
        (t/is (str/starts-with? ct "text/html") ct)))))
