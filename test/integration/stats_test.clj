(ns integration.stats-test
  (:require
   [clojure.set :as set]
   [clojure.test :as t]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.db-testing :as db-testing]
   [wormbase.api-test-client :as api-tc]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn stats-summary
  ([extra-headers]
   (api-tc/summary "stats" nil :extra-headers extra-headers))
  ([]
   (stats-summary nil)))

(t/deftest test-summary
  (t/testing "Stats summary renders with ok response on first access."
    (let [response (stats-summary)]
      (t/is (ru-hp/ok? response))
      (t/is (set/subset? #{:gene :variation :sequence-feature} (:body response))))))
