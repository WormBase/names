(defproject wormbase-names "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :plugins
  [
   [lein-environ "1.1.0"]
   [lein-pprint "1.1.2"]
   [lein-ring "0.12.0"]
   [lein-tools-deps "0.4.1"]]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :uberjar-name "app.jar"
  :ring {:handler wormbase.names.service/app
         :host "0.0.0.0"
         :init wormbase.names.service/init}
  :source-paths ["src"]
  :resource-paths ["resources"]
  :target-path "target/%s"
  :main ^:skip-aot wormbase.names.import-genes
  :monkeypatch-clojure-test false
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :aliases {"import-genes" ["run" "-m" "org.wormbase.names.import-genes"]}
  :profiles
  {:provided
   {:dependencies
    [[com.datomic/datomic-pro "0.9.5661"]]}
   :dev
   {:lein-tools-deps/config {:resolve-aliases [:dev]}
    :aliases {"code-qa" ["do" ["eastwood"] "test"]
              "spec-test" ["run" "-m" "spec-test"]}
    :plugins
    [[com.jakemccrary/lein-test-refresh "0.20.0"]
     [jonase/eastwood "0.2.4" :exclusions [org.clojure/clojure]]
     [lein-ancient "0.6.10" :exclusions [org.clojure/clojure]]]
    :ring {:nrepl {:start? true}}}
   :test
   {:lein-tools-deps/config {:resolve-aliases [:test]}
    :env {:wb-db-uri "datomic:mem://test-db"}}})
