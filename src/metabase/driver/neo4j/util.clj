(ns metabase.driver.neo4j.util
  (:import [org.neo4j.driver
            Driver])
  (:require
   [neo4clj.client :as neo4j]
   [clojure.tools.logging :as log]
   [metabase.util
    [i18n :refer [trs]]]
   [metabase
    [util :as u]]))

(def ^:dynamic ^Driver *neo-connection*
  "Connection to a Neo4j database. Bound by top-level `with-neo-connection` so it may be reused within its body."
  nil)

(defn get-neo-connection
  [{host :host port :port user :user password :password protocol :protocol}]
  (let [url (str protocol "://" host ":" port)]
    (if password (neo4j/connect url user password) (neo4j/connect url user))))

(defn -with-neo-connection
  "Run `f` with a new connection (bound to `*neo-connection*`) with `details`. Don't use this directly; use
  `with-neo-connection`."
  [f details]
  (let [connection (get-neo-connection details)]
    (log/debug (u/format-color 'cyan (trs "Opened new Neo4j connection.")))
    (try
      (binding [*neo-connection* connection]
        (f *neo-connection*))
      (finally
        (neo4j/disconnect connection)
        (log/debug (u/format-color 'cyan (trs "Closed Neo4j connection.")))))))

; TODO: use steady connection pool with idle timeout instead of one-off use
(defmacro with-neo-connection
  "Open a new neo4j connection based on DB ``details`, bind connection to `binding`, execute `body`, and
  close the connection. The DB connection is re-used by subsequent calls to `with-neo-connection` within
  `body`."
  [[binding details] & body]
  `(let [f# (fn [~binding]
              ~@body)]
     (if *neo-connection*
       (f# *neo-connection*)
       (-with-neo-connection f# ~details))))