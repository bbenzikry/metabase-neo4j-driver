(ns metabase.driver.neo4j.execute
  (:require [clojure.core.async :as a]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [java-time :as t]
            [metabase
             [driver :as driver]
             [util :as u]]
            [metabase.driver.sql-jdbc
             [execute :as sql-jdbc.execute]
             [connection :as sql-jdbc.conn]
             [sync :as sql-jdbc.sync]]
            [metabase.mbql.util :as mbql.u]
            [metabase.query-processor
             [context :as context]
             [interface :as qp.i]
             [store :as qp.store]
             [timezone :as qp.timezone]
             [util :as qputil]]
            [metabase.util.i18n :refer [trs]]
            [potemkin :as p])
  (:import [java.sql Connection JDBCType PreparedStatement ResultSet ResultSetMetaData Types]
           [java.time Instant LocalDate LocalDateTime LocalTime OffsetDateTime OffsetTime ZonedDateTime]
           javax.sql.DataSource))


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