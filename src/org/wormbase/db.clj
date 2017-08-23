(ns org.wormbase.db
  (:require
   [clojure.string :as str]
   [datomic.api :as d]
   [environ.core :as environ]
   [mount.core :as mount]
   [org.wormbase.db.schema :as db-schema]))

(def ^:dynamic *wb-db-uri* nil)

(defn connect
  "Connects to the datomic database and transacts schema if required."
  [uri]
  (let [conn (d/connect uri)]
    (db-schema/install conn 1)
    conn))

(defn checked-connect
  "Version of connect that checks that the datomic URI matches prefixes.
  Designed to be used with `mount/start-with` for testing/development."
  [uri allowed-uri-prefixes]
  (if (some (partial str/starts-with? uri) allowed-uri-prefixes)
    (connect uri)
    (throw (ex-info
            (str "Refusing to connect - "
                 "URI did not match any permitted prefix.")
            {:uri uri
             :allowed-uri-prefixes allowed-uri-prefixes}))))

(defn checked-delete
  [uri]
  (when (str/starts-with? uri "datomic:men")
    (d/delete-database uri)))

(defn scratch-connect [uri]
  (d/delete-database uri)
  (d/create-database uri)
  (checked-connect uri ["datomic:mem" "datomic:dev"]))

(mount/defstate conn
  :start (binding [*wb-db-uri* (environ/env :wb-db-uri)]
           (if (str/starts-with? *wb-db-uri* "datomic:mem")
             (scratch-connect *wb-db-uri*)
             (connect *wb-db-uri*)))
  :stop (d/release conn))

(defn connected? []
  (let [states (mount/running-states)
        state-key (pr-str #'conn)]
    (states state-key)))

