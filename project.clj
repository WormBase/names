(defproject wormbase-names "0.1.1-SNAPSHOT"
  :description "WormBase names service."
  :plugins
  [[lein-environ "1.1.0"]
   [lein-ring "0.12.0"]
   [lein-tools-deps "0.4.1"]]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :jvm-opts ["-XX:-OmitStackTraceInFastThrow"]
  :uberjar-name "app.jar"
  :ring {:handler wormbase.names.service/app
         :host "0.0.0.0"
         :init wormbase.names.service/init}
  :resource-paths ["resources" "client/build"]
  :target-path "target/%s"
  :main ^:skip-aot wormbase.names.importer
  :monkeypatch-clojure-test false
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :aliases {"import" ["run" "-m" "org.wormbase.names.importer"]
            "update-eb-docker-container-version" ["run" "-m" "wormbase.aws-eb-setup"]
            "aws-ecr-publish" ["do"
                               ["shell" "make" "clean"]
                               ["shell" "make" "docker-build"]
                               ["shell" "make" "docker-ecr-login"]
                               ["shell" "make" "docker-tag"]
                               ["shell" "make" "docker-push-ecr"]]}
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["update-eb-docker-container-version"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["aws-ecr-publish"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :profiles
  {:provided
   {:lein-tools-deps/config {:resolve-aliases [:datomic-pro :logging]}}
   :datomic-pro
   {:lein-tools-deps/config {:resolve-aliases [:datomic-pro :logging]}}
   :prod
   {:lein-tools-deps/config {:resolve-aliases [:datomic-pro :logging :prod]
                             :aliases [:datomic-pro :logging :prod]}}
   :dev
   {:lein-tools-deps/config {:resolve-aliases [:datomic-pro :dev]
                             :aliases [:datomic-pro :dev]}
    :aliases {"code-qa" ["do" ["eastwood"] "test"]
              "spec-test" ["run" "-m" "spec-test"]}
    :plugins
    [[com.jakemccrary/lein-test-refresh "0.20.0"]
     [jonase/eastwood "0.2.4" :exclusions [org.clojure/clojure]]
     [lein-ancient "0.6.10" :exclusions [org.clojure/clojure]]
     [lein-pprint "1.1.2"]
     [lein-shell "0.5.0"]]
    :ring {:nrepl {:start? true}}}
   :test
   {:lein-tools-deps/config {:resolve-aliases [:test]}
    :env {:wb-db-uri "datomic:mem://test-db"}}
   :repl
   {:dependencies
    [[nrepl "0.6.0"]
     [acyclic/squiggly-clojure "0.1.9-SNAPSHOT" :exclusions [org.clojure/tools.reader]]]
    :plugins
    [[refactor-nrepl "2.4.0"]
     [cider/cider-nrepl "0.21.1"]]}})
