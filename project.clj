(defproject wormbase-names "0.5.5-SNAPSHOT"
  :description "WormBase names service."
  :plugins
  [[lein-tools-deps "0.4.1"]]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :resource-paths ["resources" "client/build"]
  :target-path "target/%s"
  :main ^:skip-aot wormbase.names.importer
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
                  ["vcs" "push"]])
