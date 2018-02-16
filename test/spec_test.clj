(ns spec-test
  (:require
   [clojure.spec.test.alpha :as stest]
   [clojure.tools.namespace.repl :refer [refresh]]))

(defn run-analysis
  []
  (stest/instrument)
  (stest/summarize-results (stest/check)))

(defn -main
  [& args]
  (refresh :after 'spec-test/run-analysis))

