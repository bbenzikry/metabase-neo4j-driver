(defproject metabase/neo4j-driver "0.0.3-SNAPSHOT-neo4j-connector-1.0.0"
  :min-lein-version "2.5.0"
  ; git repo support
  :plugins [[lein-tools-deps "0.4.5"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :aliases
  {"test"       ["with-profile" "+unit_tests" "test"]}

  :profiles
  {:provided
   {:dependencies
    [[metabase-core "1.0.0-SNAPSHOT"]]}
   :uberjar
   {:auto-clean    true
    :aot           :all
    :javac-options ["-target" "1.8", "-source" "1.8"]
    :target-path   "target/%s"
    :uberjar-name  "neo4j.metabase-driver.jar"}}
  :unit_tests
  {:test-paths     ^:replace ["test_unit"]})