(ns integration.test-find-gene-by-any-name
  (:require
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :as t]
   [org.wormbase.fake-auth :as fake-auth]
   [org.wormbase.test-utils :as tu]
   [org.wormbase.db-testing :as db-testing]
   [org.wormbase.names.service :as service]
   [clojure.string :as str]))

(t/use-fixtures :each db-testing/db-lifecycle)

(defn find-gene
  [pattern & {:keys [current-user]
              :or {current-user "tester@wormbase.org"}}]
  (binding [fake-auth/*gapi-verify-token-response* {"email" current-user}]
    (let [current-user-token (get fake-auth/tokens current-user)
          params {:pattern pattern}
          headers {"content-type" "application/edn"
                   "authorization" (str "Token " current-user-token)}
          [status body] (tu/get*
                         service/app
                         (str "/gene/")
                         params
                         headers)]
      [status (tu/parse-body body)])))

(defn- rand-prefix [string]
  (subs string 0 (max 1 (-> (rand)
                            (* (count string))
                            (Math/floor)
                            (int)))))

(t/deftest find-by-any-name
  (let [data-samples (tu/gene-samples 10)]
    (tu/with-fixtures
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
        (t/testing "Get an validation errror (400) result for invalid find terms."
          (doseq [term [""]]
            (let [[status body] (find-gene term)]
              (tu/status-is? 400 status body)
              (t/is (not (contains? body :matches)))
              (t/is (re-matches #".*validation failed.*" (get body :message ""))
                    (pr-str "BODY:" body))
              (t/is (:problems body) (pr-str body)))))
        (t/testing "Results found for matching GeneID/CGC/sequence prefixes"
          (doseq [attr [:gene/id :gene/cgc-name :gene/sequence-name]]
            (let [sample (rand-nth data-samples)
                  gid (:gene/id sample)
                  value (attr sample)
                  valid-prefix (rand-prefix value)
                  [status body] (find-gene valid-prefix)
                  matches (:matches body)]
              (tu/status-is? 200 status body)
              (t/is (not (empty? matches))
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
                         (pr-str matches))))))))))
