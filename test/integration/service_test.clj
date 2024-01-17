(ns integration.service-test
  (:require
   [clojure.test :as t]
   [ring.util.http-predicates :as ru-hp]
   [ring.util.response :as ring-response]
   [wormbase.db-testing :as db-testing]
   [wormbase.fake-auth]
   [wormbase.names.service :as service]))

(t/use-fixtures :once db-testing/db-lifecycle)

(t/deftest fallback-404
  (t/testing "When no routes are matched in request processing, client api is served."
    (let [response (service/app
                    {:uri "/client/route/like/this"
                     :headers {"accept" "text/html"
                               "authorization" "Token FAKED"}
                     :query-params {}
                     :request-method :get})]
      (t/is (not (nil? response)))
      (t/is (ru-hp/ok? response))
      (t/is (ring-response/get-header response "content-type") "text/html"))))

