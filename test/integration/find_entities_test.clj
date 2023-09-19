(ns integration.find-entities-test
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as str]
   [clojure.test :as t]
   [ring.util.http-predicates :as ru-hp]
   [wormbase.db-testing :as db-testing]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.gen-specs.variation :as gsv]
   [wormbase.names.service :as service]
   [wormbase.test-utils :as tu]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn find-variation
  [pattern & {:keys [current-user]
              :or {current-user "tester@wormbase.org"}}]
  (binding [fake-auth/*gapi-verify-token-response* (fake-auth/payload {"email" current-user})]
    (let [params {:pattern pattern}
          headers {"accept" "application/json"
                   "authorization" "Token IsTotallyMadeUp"}
          [status body] (tu/get*
                         service/app
                         "/api/entity/variation"
                         params
                         headers)]
      [status (tu/parse-body body)])))

(defn- rand-prefix [string]
  (subs string 0 (max 1 (-> (rand)
                            (* (count string))
                            (Math/floor)
                            (int)))))

(t/deftest find-by-any-name
  (t/testing "Get an validation errror (400) result for invalid find terms."
    (tu/with-installed-generic-entity
      :variation/id
      "WBV%d"
      (fn [_]
        (doseq [term [""]]
          (let [[status body] (find-variation term)]
            (t/is (ru-hp/bad-request? {:status status :body body}))
            (t/is (not (contains? body :matches)))
            (t/is (re-matches #".*validation failed.*"
                              (get body :message "")))
            (t/is (:problems body) (pr-str body)))))))
  (let [ids (gen/sample gsv/id 2)
        names (gen/sample gsv/name 2)
        stati (repeat 2 :variation.status/live)
        fixtures (map #(zipmap [:variation/id :variation/name :variation/status] [%1 %2 %3])
                      ids names stati)]
    (tu/with-variation-fixtures
      fixtures
      (fn test-find-cases [_]
        (t/testing "Get a 200 response for a non-matching find term"
          (let [[status body] (find-variation "foobar")
                matches (:matches body)]
            (t/is (ru-hp/ok? {:status status :body body}))
            (t/is (empty? matches))))
        (t/testing "Whitepace at begining and end of find term is ignoreed"
          (doseq [term [" foo" "bar "]]
            (let [[status body] (find-variation term)
                  matches (:matches body)]
              (t/is (ru-hp/ok? {:status status :body body}))
              (t/is (empty? matches)))))
        (t/testing "Results found for matching variation prefixes"
          (let [sample (rand-nth fixtures)
                id (:variation/id sample)
                value (:variation/name sample)]
            (when value
              (let [valid-prefix (rand-prefix value)
                    [status body] (find-variation valid-prefix)
                    matches (:matches body)]
                (t/is (ru-hp/ok? {:status status :body body}))
                (t/is (seq matches)
                      (str "No matches found for " valid-prefix))
                (t/is (some (fn [match] (= (:id match) id)) matches)
                      (str "Could not find any Variation ID matching " id
                           " matches:" (pr-str matches)))
                (t/is (some (fn [match]
                              (assert (not (nil? match)))
                              (str/starts-with? (:name match) valid-prefix))
                            matches)
                      (str "Could not verify and match startswith prefix "
                           (pr-str valid-prefix) ""
                           (pr-str body)))))))))))
