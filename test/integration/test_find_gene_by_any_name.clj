(ns integration.test-find-gene-by-any-name
  (:require
   [clojure.string :as str]
   [clojure.test :as t]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.test-utils :as tu]
   [wormbase.db-testing :as db-testing]
   [wormbase.names.service :as service]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn make-auth-payload
  [& {:keys [current-user]
      :or {current-user "tester@wormbase.org"}}]
  (fake-auth/payload {"email" current-user}))

(defn find-gene
  [pattern & {:keys [current-user]
              :or {current-user "tester@wormbase.org"}}]
  (binding [fake-auth/*gapi-verify-token-response* (fake-auth/payload {"email" current-user})]
    (let [params {:pattern pattern}
          headers {"accept" "application/json"
                   "authorization" "Token IsTotallyMadeUp"}
          [status body] (tu/get*
                         service/app
                         "/api/gene/"
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
    (doseq [term [""]]
      (let [[status body] (find-gene term)]
        (tu/status-is? 400 status body)
        (t/is (not (contains? body :matches)))
        (t/is (re-matches #".*validation failed.*"
                          (get body :message "")))
        (t/is (:problems body) (pr-str body)))))
  (let [data-samples (tu/gene-samples 3)]
    (tu/with-gene-fixtures
      data-samples
      (fn test-find-cases [conn]
        (t/testing "Get a 200 response for a non-matching find term"
          (let [[status body] (find-gene "foobar")
                matches (:matches body)]
            (tu/status-is? 200 status body)
            (t/is (empty? matches))))
        (t/testing "Whitepace at begining and end of find term is ignoreed"
          (doseq [term [" foo" "bar "]]
            (let [[status body] (find-gene term)
                  matches (:matches body)]
              (tu/status-is? 200 status body)
              (t/is (empty? matches)))))
        (t/testing "Results found for matching GeneID/CGC/sequence prefixes"
        (doseq [attr [:gene/id :gene/cgc-name :gene/sequence-name]]
          (let [sample (rand-nth data-samples)
                gid (:gene/id sample)
                value (attr sample)]
            (when value
              (let [valid-prefix (rand-prefix value)
                    [status body] (find-gene valid-prefix)
                    matches (:matches body)]
                (tu/status-is? 200 status body)
                (t/is (seq matches)
                      (str "No matches found for " valid-prefix))
                (t/is (some (fn [match] (= (:gene/id match) gid)) matches)
                      (str "Could not find any GeneID matching " gid
                           " matches:" (pr-str matches)))
                (t/is (some (fn [match]
                              (assert (not (nil? match)))
                              (str/starts-with? (attr match) valid-prefix))
                            matches)
                      (str "Could not verify and match startswith prefix "
                           (pr-str valid-prefix) ""
                           (pr-str matches))))))))))))
