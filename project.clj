(defproject wormbase-names "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies
  [[org.clojure/clojure "1.9.0"]
   ;; TODO: figure out how to configure logging
   ;; When this package is added, no logging statements appear in
   ;; console by default (compojure-api integration)
   ;; [org.clojure/tools.logging "0.4.0"]
   [org.clojure/core.cache "0.6.5"]
   [aero "1.1.2"]
   [bk/ring-gzip "0.2.1"]
   [buddy/buddy-auth "2.1.0"]
   [cheshire "5.8.0"]
   [clj-http "3.7.0"]
   [clojure.java-time "0.3.1"]
   [com.velisco/strgen "0.1.5"]
   [danlentz/clj-uuid "0.1.7"]
   [environ "1.1.0"]
   [expound "0.5.0"]
   [com.google.api-client/google-api-client "1.23.0"]
   [io.rkn/conformity "0.5.1"]
   [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                      javax.jms/jms
                                      com.sun.jdmk/jmxtools
                                      com.sun.jmx/jmxri]]
   [me.raynes/fs "1.4.6"]
   [metosin/compojure-api "2.0.0-alpha16" :exclusions [potemkin
                                                       instaparse]]
   [metosin/spec-tools "0.5.1"]
   [mount "0.1.12"]
   ;; [phrase "0.3-alpha3"] TODO: use for better API error messages.
   [ring/ring-codec "1.1.0"]
   [ring/ring-core "1.6.3"]
   [ring/ring-defaults "0.3.1"]
   [ring/ring-jetty-adapter "1.6.3"]]
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
    [[com.datomic/datomic-pro "0.9.5561"]]}
   :dev
   {:aliases {"code-qa" ["do" ["eastwood"] "test"]
              "spec-test" ["run" "-m" "spec-test"]}
    :dependencies
    [[org.clojure/test.check "0.9.0"]
     [org.clojure/tools.trace "0.7.9"]
     [com.gfredericks/test.chuck "0.2.8"]
     [datomic-schema-grapher "0.0.1"]
     [javax.servlet/javax.servlet-api "4.0.0"]
     [ring/ring-devel "1.6.3"]
     [peridot "0.5.0"]
     [vvvvalvalval/datomock "0.2.0"]
     [ring/ring-mock "0.3.2"]
     [specviz "0.2.4" :exclusions [com.google.guava/guava
                                   com.google.code.findbugs/jsr305]]]
    :plugins
    [[com.jakemccrary/lein-test-refresh "0.20.0"]
     [jonase/eastwood "0.2.4" :exclusions [org.clojure/clojure]]
     [lein-ancient "0.6.10" :exclusions [org.clojure/clojure]]]
    :ring {:nrepl {:start? true}}}
   :test
   {:env
    {:wb-db-uri "datomic:mem://test-db"}}})
