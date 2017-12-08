(defproject wormbase-names "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.9.0-RC2"]
                 ;; [org.clojure/tools.logging "0.4.0"]
                 [org.clojure/core.cache "0.6.5"]
                 [bk/ring-gzip "0.2.1"]
                 [cheshire "5.8.0"]
                 [circleci/clj-yaml "0.5.6"]
                 [clj-http "3.7.0"]
                 [clj-time "0.14.2"]
                 [com.velisco/strgen "0.1.4"]
                 [datomic.schema "0.1.12"]
                 [environ "1.1.0"]
                 [expound "0.3.4"]
                 [io.rkn/conformity "0.5.1"]
                 [me.raynes/fs "1.4.6"]
                 [metosin/compojure-api "2.0.0-alpha16"
                  :exclusions [potemkin instaparse]]
                 ;; TODO: upgrade to alpha8 when released.
                 ;; [metosin/spec-tools "0.3.3"] alpha 8 and the 0.4.0
                 ;; point release below fix a bug that's critical to
                 ;; the use of s/map-of s/coll-of for specs with
                 ;; compojure-api
                 ;; [metosin/spec-tools "0.4.0-20170918.103911-2"]
                 ;; [metosin/spec-tools "0.4.0-SNAPSHOT"]

                 ;; https://github.com/metosin/spec-tools/issues/79
                 [metosin/spec-tools "0.5.1"]
                 [mount "0.1.11"]
                 [ring-oauth2 "0.1.4"]
                 [ring/ring-codec "1.1.0"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-defaults "0.3.1"]
                 [ring/ring-jetty-adapter "1.6.3"]
                 [specviz "0.2.4"
                  :exclusions [com.google.guava/guava
                               com.google.code.findbugs/jsr305]]]
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
    [[com.datomic/datomic-pro "0.9.5561.56"]]}
   :dev
   {:aliases {"code-qa" ["do" ["eastwood"] "test"]
              "spec-test" ["run" "-m" "spec-test"]}
    :dependencies [[org.clojure/test.check "0.9.0"]
                   [com.gfredericks/test.chuck "0.2.8"]
                   [javax.servlet/javax.servlet-api "4.0.0"]
                   [org.clojure/tools.trace "0.7.9"]
                   [ring/ring-devel "1.6.3"]
                   [peridot "0.5.0"]
                   [vvvvalvalval/datomock "0.2.0"]
                   [ring/ring-mock "0.3.2"]
                   [specviz "0.2.4"]]
    :plugins [[com.jakemccrary/lein-test-refresh "0.20.0"]
              [jonase/eastwood "0.2.4"
               :exclusions [org.clojure/clojure]]
              [lein-ancient "0.6.10"
               :exclusions [org.clojure/clojure]]]
    :ring {:nrepl {:start? true}}}
   :test
   {:env
    {:wb-db-uri "datomic:mem://test-db"}}})
