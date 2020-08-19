(defproject metabase/neo4j-driver "1.0.0"
  :min-lein-version "2.5.0"

  ; :repositories [["bintray" "https://dl.bintray.com/meetr/thirdparty"]]

  :dependencies
  [[neo4j/neo4j-bi-jdbc "1.0.0"]]

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