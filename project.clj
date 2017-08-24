(defproject wormbase-names "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/tools.logging "0.4.0"]
                 [bk/ring-gzip "0.2.1"]
                 [clj-time "0.14.0"]
                 [com.velisco/strgen "0.1.4"]
                 [datomic.schema "0.1.11"]
                 [environ "1.1.0"]
                 [io.rkn/conformity "0.5.0"]
                 [metosin/compojure-api "2.0.0-alpha7"]
                 [metosin/spec-tools "0.3.2"]
                 [mount "0.1.11"]]
  :plugins [[lein-environ "1.1.0"]
            [lein-pprint "1.1.2"]
            [lein-ring "0.12.0"]]
  :uberjar-name "server.jar"
  :uberjar {:aot :all}
  :ring {:handler org.wormbase.names.service/app
         :host "0.0.0.0"
         :init org.wormbase.names.service/init}
  :source-paths ["src"]
  :resource-paths ["resources"]
  :target-path "target/%s"
  :main ^:skip-aot org.wormbase.names.service
  :monkeypatch-clojure-test false
  :profiles
  {:provided
   {:dependencies
    [[com.datomic/datomic-pro "0.9.5561.54"]]}
   :dev
   {:aliases {"code-qa" ["do" ["eastwood"] "test"]
              "spec-test" ["run" "-m" "spec-test"]}
    :dependencies [[org.clojure/test.check "0.9.0"]
                   [com.gfredericks/test.chuck "0.2.8"]
                   [javax.servlet/javax.servlet-api "4.0.0"]
                   [org.clojure/tools.trace "0.7.9"]
                   [ring/ring-devel "1.6.2"]
                   [peridot "0.4.4"]
                   [specviz "0.2.3"]]
    :plugins [[com.jakemccrary/lein-test-refresh "0.20.0"]
              [jonase/eastwood "0.2.4"
               :exclusions [org.clojure/clojure]]
              [lein-ancient "0.6.10"
               :exclusions [org.clojure/clojure]]]
    :ring {:nrepl {:start? true}}}
   :test
   {:env
    {:wb-db-uri "datomic:mem://test-db"}}})


