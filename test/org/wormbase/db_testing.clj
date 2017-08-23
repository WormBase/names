(ns org.wormbase.db-testing
  (:require
   [clj-time.coerce :as ctc]
   [clj-time.core :as ct]
   [clj-time.core :as ct]
   [clojure.string :as str]
   [clojure.test :as t]
   [datomic.api :as d]
   [mount.core :as mount]
   [org.wormbase.db :as owdb]))
  
(defn db-lifecycle [f]
  (let [uri (str "datomic:mem://" *ns* "-" (ctc/to-long (ct/now)))]
    (binding [owdb/*wb-db-uri* uri]
      (mount/start-with
       {#'owdb/conn
        (owdb/scratch-connect uri)})
      (f)
      (owdb/checked-delete uri)
      (mount/stop))))

