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

(defn indexes-of [e coll]
  (keep-indexed #(if (= e %2) %1) coll))

(t/deftest find-by-any-name
  (let [[identifiers samples] (tu/gene-samples 10)
        ids-indexed (keep-indexed (fn [idx id] [idx id]) identifiers)
        data-samples (keep-indexed (fn [idx sample]
                                     (let [cgc-name (partial tu/cgc-name-for-sample
                                                             sample)
                                           seq-name (partial tu/seq-name-for-sample
                                                             sample)]
                                       (assoc sample
                                              :gene/id (second (nth ids-indexed idx))
                                              :gene/cgc-name (cgc-name)
                                              :gene/sequence-name (seq-name))))
                                   samples)]
    (println "DATA SAMPLES 1:" data-samples)
    (tu/with-fixtures
      data-samples
      (fn test-find-cases [conn]
        (t/testing "Get an empty result for no matches."
          (let [[status body] (find-gene "foobar")
                matches (:matches body)]
            (tu/status-is? 200 status body)

            (t/is (empty? matches))))
        (t/testing "Results found for matching CGC name prefix"
          (let [sample (rand-nth data-samples)
                gid (nth identifiers (first (indexes-of sample data-samples)))
                valid-prefix (-> sample :gene/cgc-name (subs 0 2))
                _ (prn "testing with valid prefix?:" valid-prefix)
                [status body] (find-gene valid-prefix)
                matches (:matches body)]
            (tu/status-is? 200 status body)
            (t/is (some #(= (:gene/id %) gid) matches))
            (t/is (some #(str/starts-with? (:gene/cgc-name %) valid-prefix)
                        matches))))))))
      
