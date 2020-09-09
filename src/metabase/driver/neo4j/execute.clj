(ns metabase.driver.neo4j.execute
  (:import [org.neo4j.driver
            Driver])
  (:require
   [metabase.driver.neo4j.util
    :refer [with-neo-connection]]
   [neo4clj.client :as neo4j]
   [clojure.core.async :as a]
   [clojure.tools.logging :as log]
   [metabase.driver.sql-jdbc
    [execute :as sql-jdbc.execute]]
   [metabase.mbql.util :as mbql.u]
   [metabase.query-processor
    [context :as context]
    [interface :as qp.i]
    [store :as qp.store]
    [reducible :as qp.reducible]
    [timezone :as qp.timezone]]))


; We want to do this in order to avoid remarks and commenting which affect the simba JDBC driver


(defn execute-reducible-query
  "Implementation of `execute-reducible-query` for neo4j bi connector driver
   Copied as is from sql-jdbc/execute"
  {:added "0.35.0", :arglists '([driver query context respond])}
  [driver {{sql :query, params :params} :native, :as outer-query} context respond]
  {:pre [(string? sql) (seq sql)]}
  (let [max-rows (or (mbql.u/query->max-rows-limit outer-query)
                     qp.i/absolute-max-results)]
    (with-open [conn (sql-jdbc.execute/connection-with-timezone driver (qp.store/database) (qp.timezone/report-timezone-id-if-supported))
                stmt (doto (#'sql-jdbc.execute/prepared-statement* driver conn sql params (context/canceled-chan context))
                       (.setMaxRows max-rows))
                rs   (sql-jdbc.execute/execute-query! driver stmt)]
      (let [rsmeta           (.getMetaData rs)
            results-metadata {:cols (sql-jdbc.execute/column-metadata driver rsmeta)}]
        (respond results-metadata (sql-jdbc.execute/reducible-rows driver rs rsmeta (context/canceled-chan context)))))))

; TODO add with-neo4j-connection macro
(defn get-neo-connection
  [{host :host port :port user :user password :password dbname :dbname}]
  (let [base (str "bolt://" host ":" port)
        url (if dbname (str base "/" dbname) base)]
    (if password (neo4j/connect url user password) (neo4j/connect url user))))

(defn get-cypher-columns [result]
  (if (seq? result)
    {:cols (into [] (map #(assoc {} :name %) (keys (first (take 1 result)))))}
    {:cols [{:name "result"}]}))

(defn execute-reducible-query->cypher
  "Process and run a native cypher query."
  [_ {{query :query} :native} context respond]
  (log/info "Executing reducible query for cypher")
  (with-neo-connection [^Driver connection (:details (qp.store/database))]
    (let [results (volatile! (neo4j/execute! connection query))
          nonseq-val (volatile! false)
          columns (get-cypher-columns @results)
          row-thunk #(if-not (seq? @results) ((if-not @nonseq-val (vreset! nonseq-val true) @results) nil)
                             (let [old @results]
                               (vswap! results (fn [state] (drop 1 state)))
                               (vals (first (take 1 old)))))]
    ; handle cancellation
      (a/go
        (when (a/<! (context/canceled-chan context))
          (neo4j/disconnect connection)))
      (respond columns (qp.reducible/reducible-rows row-thunk (context/canceled-chan context))))))