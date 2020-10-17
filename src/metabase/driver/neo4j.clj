(ns metabase.driver.neo4j
  "Metabase Neo4j Driver."
  (:import  [org.neo4j.cypher.internal.parser CypherParser]
            [org.neo4j.cypher.internal.util OpenCypherExceptionFactory])
  (:require [clojure
             [set :as set]]
            [clojure.tools.logging :as log]
            [honeysql.core :as hsql]
            [metabase.util
             [honeysql-extensions :as hx]]
            [metabase.driver :as driver]
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.driver.neo4j
            ;;  [query-processor :as qp]
             [util :as neo4j.util]
             [execute :as neo4j.execute]]
            [metabase.driver.sql-jdbc
             [common :as sql-jdbc.common]
             [connection :as sql-jdbc.conn]
             [sync :as sql-jdbc.sync]]))

(driver/register! :neo4j, :parent :sql-jdbc)
(defmethod driver/display-name :neo4j [_] "Neo4j 4")
(def accepted-neo-protocols (sorted-set "bolt" "bolt+s" "bolt+ssc" "neo4j" "neo4j+s" "neo4j+ssc"))
(defn- make-subname [host port db jdbc-flags]
  (str "//" host ":" port "/" db jdbc-flags))

; Support multiple :classname
(defn neo4j
  "Create a Clojure JDBC database specification for Neo4j."
  [{:keys [host port db jdbc-flags]
    :or   {host "localhost", port 7687, db "neo4j", jdbc-flags ""}
    :as   opts}]
  (merge
   {:classname   "com.simba.neo4j.jdbc42.Driver"
    :subprotocol "neo4j"
    :subname     (make-subname host port db jdbc-flags)}
   (dissoc opts :host :port :db jdbc-flags)))

; TODO: Create a more prceise list ( this is a simple copy )
; type mapping
(def ^:private db-type->base-type
  {:ARRAY                               :type/*
   :BIGINT                              :type/BigInteger
   :BINARY                              :type/*
   :BIT                                 :type/Boolean
   :BLOB                                :type/*
   :BOOL                                :type/Boolean
   :BOOLEAN                             :type/Boolean
   :BYTEA                               :type/*
   :CHAR                                :type/Text
   :CHARACTER                           :type/Text
   :CLOB                                :type/Text
   :DATE                                :type/Date
   :DATETIME                            :type/DateTime
   :DEC                                 :type/Decimal
   :DECIMAL                             :type/Decimal
   :DOUBLE                              :type/Float
   :FLOAT                               :type/Float
   :FLOAT4                              :type/Float
   :FLOAT8                              :type/Float
   :GEOMETRY                            :type/*
   :IDENTITY                            :type/Integer
   :IMAGE                               :type/*
   :INT                                 :type/Integer
   :INT2                                :type/Integer
   :INT4                                :type/Integer
   :INT8                                :type/BigInteger
   :INTEGER                             :type/Integer
   :LONGBLOB                            :type/*
   :LONGTEXT                            :type/Text
   :LONGVARBINARY                       :type/*
   :LONGVARCHAR                         :type/Text
   :MEDIUMBLOB                          :type/*
   :MEDIUMINT                           :type/Integer
   :MEDIUMTEXT                          :type/Text
   :NCHAR                               :type/Text
   :NCLOB                               :type/Text
   :NTEXT                               :type/Text
   :NUMBER                              :type/Decimal
   :NUMERIC                             :type/Decimal
   :NVARCHAR                            :type/Text
   :NVARCHAR2                           :type/Text
   :OID                                 :type/*
   :OTHER                               :type/*
   :RAW                                 :type/*
   :REAL                                :type/Float
   :SIGNED                              :type/Integer
   :SMALLDATETIME                       :type/DateTime
   :SMALLINT                            :type/Integer
   :TEXT                                :type/Text
   :TIME                                :type/Time
   :TIMESTAMP                           :type/DateTime
   :TINYBLOB                            :type/*
   :TINYINT                             :type/Integer
   :TINYTEXT                            :type/Text
   :UUID                                :type/Text
   :VARBINARY                           :type/*
   :VARCHAR                             :type/Text
   :VARCHAR2                            :type/Text
   :VARCHAR_CASESENSITIVE               :type/Text
   :VARCHAR_IGNORECASE                  :type/Text
   :YEAR                                :type/Integer})

(defmethod sql.qp/date [:neo4j :day]             [_ _ expr] (hx/->date expr))
(defmethod sql.qp/date [:neo4j :minute]          [_ _ expr] (hsql/call :trunc expr :minute))
(defmethod sql.qp/date [:neo4j :minute-of-hour]  [_ _ expr] (hsql/call :minute expr))
(defmethod sql.qp/date [:neo4j :hour]            [_ _ expr] (hsql/call :trunc expr :hour))
(defmethod sql.qp/date [:neo4j :hour-of-day]     [_ _ expr] (hsql/call :hour expr))
(defmethod sql.qp/date [:neo4j :day-of-month]    [_ _ expr] (hsql/call :dayofmonth expr))
(defmethod sql.qp/date [:neo4j :day-of-year]     [_ _ expr] (hsql/call :dayofyear expr))
(defmethod sql.qp/date [:neo4j :day-of-week]     [_ _ expr] (hsql/call :dayofweek expr))
(defmethod sql.qp/date [:neo4j :week]            [_ _ expr] (hsql/call :trunc expr :week)) ; Y = week year; w = week in year
(defmethod sql.qp/date [:neo4j :week-of-year]    [_ _ expr] (hsql/call :week expr))
(defmethod sql.qp/date [:neo4j :month]           [_ _ expr] (hsql/call :trunc expr :month))
(defmethod sql.qp/date [:neo4j :month-of-year]   [_ _ expr] (hsql/call :month expr))
(defmethod sql.qp/date [:neo4j :quarter-of-year] [_ _ expr] (hx/quarter expr))
(defmethod sql.qp/date [:neo4j :year]            [_ _ expr] (hsql/call :year expr))
(defmethod sql.qp/unix-timestamp->honeysql [:neo4j :seconds]
  [_ _ expr]
  (hx/* expr 1000))


; ;;; metabase.driver features
(doseq [feature [:native-parameters]]
  (defmethod driver/supports? [:neo4j feature] [_ _] true))

; ;;; 
; ;;; |                                         metabase.driver.sql-jdbc impls                                         |
; ;;; +----------------------------------------------------------------------------------------------------------------+

(defmethod sql-jdbc.sync/database-type->base-type :neo4j
  [_ database-type]
  (db-type->base-type database-type))

(defmethod sql-jdbc.conn/connection-details->spec :neo4j
  [_ details]
  (when-not (contains? accepted-neo-protocols (:protocol details))
    (throw (Exception. (str "Protocol must be one of " (clojure.string/join ", " accepted-neo-protocols)))))
  (-> details
      (update :port (fn [port]
                      (if (string? port)
                        (Integer/parseInt port)
                        port)))
      (set/rename-keys {:dbname :db})
      neo4j
      (sql-jdbc.common/handle-additional-options details)))


;  |                                          Execution + Cypher support                                            |
;  +----------------------------------------------------------------------------------------------------------------+


(def ^:private cypher-parser (CypherParser.))
(def ^:private cypher-exception-factory (OpenCypherExceptionFactory. nil))
(defn- cypher? [query]
  (try
    (log/info "Received neo4j query. Checking if cypher ❓")
    (.parse cypher-parser query cypher-exception-factory nil)
    true (catch Throwable ex (log/debug ex) false)))

(defmethod driver/describe-table :neo4j
  [driver database table]
  (sql-jdbc.sync/describe-table driver database table))

(defmethod driver/substitute-native-parameters :neo4j
  [_ inner-query]
  (let [cypher-query (neo4j.util/replace-cypher-params inner-query)]
    (if
     (cypher? (:query cypher-query))
      (assoc cypher-query :cypher true :cypher-params (neo4j.util/map-cypher-params (:parameters inner-query)))
      (driver/substitute-native-parameters :sql inner-query))))

(defmethod driver/execute-reducible-query :neo4j
  [driver query chans respond]
  (let [is-cypher (get-in query [:native :cypher])]
    (if is-cypher (neo4j.execute/execute-reducible-query->cypher driver query chans respond) (neo4j.execute/execute-reducible-query driver query chans respond))))