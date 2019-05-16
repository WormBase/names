(ns integration.test-service
  (:require
   [clojure.string :as str]
   [clojure.test :as t]
   [ring.util.http-predicates :as ru-hp]
   [ring.util.http-response :as http-response]
   [wormbase.db-testing :as db-testing]
   [wormbase.fake-auth]
   [wormbase.names.response-formats :as wnrf]
   [wormbase.names.service :as service]))

(t/use-fixtures :once db-testing/db-lifecycle)

(t/deftest fallback-404
  (t/testing "When request uri starts with /api but matches no route"
    (let [response (service/app
                    {:uri "/api/aliens"
                     :headers {"accept" "application/json"
                               "Authorization" "Token FAKED"}
                     :query-params {}
                     :request-method :get})]
      (t/is (ru-hp/not-found? response))
      (t/is (str/starts-with?
             (peek (http-response/find-header response "Content-Type"))
             "application/json")
            (str "Wrong content-type?:" (pr-str (:headers response))))
      (t/is (contains? response :body))
      (let [response-text (some-> response :body slurp)]
        (t/is (not (nil? (:message (wnrf/decode-content response-text))))
              (str response-text)))))
  (t/testing "When no routes are matched in request processing, client api is served."
    (let [response (service/app
                    {:uri "/client/route/like/this"
                     :headers {"accept" "text/html"
                               "authorization" "Token FAKED"}
                     :query-params {}
                     :request-method :get})]
      (t/is (not (nil? response)))
      (t/is (ru-hp/ok? response))
      (t/is (http-response/get-header response "content-type") "text/html"))))

