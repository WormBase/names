(ns wormbase.db-testing
  (:require
   [clojure.core.cache :as cache]
   [datomock.core :as dm]
   [datomic.api :as d]
   [java-time :as jt]
   [mount.core :as mount]
   [wormbase.db :as wdb]
   [wormbase.db.schema :as schema]
   [wormbase.names.auth :as wna]
   [wormbase.names.util :as wnu]
   [wormbase.names.event-broadcast :as wneb]
   [wormbase.names.event-broadcast.proto :as wnebp]))

;;; fixture caching and general approach taken verbatim from:
;;; https://vvvvalvalval.github.io/posts/2016-07-24-datomic-web-app-a-practical-guide.html

(defn make-fixture-conn
  []
  (let [conn (dm/mock-conn)]
    (schema/install conn)
    ;; A set of fake users with different roles to test against.
    @(d/transact-async conn [{:person/email "tester@wormbase.org"
                              :person/id "WBPerson007"
                              :person/auth-token (wna/sign-token
                                                  (-> (wnu/read-app-config)
                                                      :auth-token)
                                                  {"email" "tester@wormbase.org"
                                                   "hd" "wormbase.org"})
                              :person/roles #{:person.role/admin}}
                             {:person/email "tester2@wormbase.org"}
                             {:person/email "tester3@wormbase.org"}
                             {:db/ident :event/test-fixture-assertion}])
    conn))

(defonce conn-cache
  (atom (cache/ttl-cache-factory {} :ttl 5000)))

(defn starting-point-conn []
  (:conn (swap! conn-cache #(if (cache/has? % :conn)
                              (cache/hit % :conn)
                              (cache/miss % :conn (make-fixture-conn))))))

(defn fixture-conn
  "Creates a Datomic connection with the schema and fixture data
  installed."
  []
  (dm/fork-conn (starting-point-conn)))

(def ^{:doc "Fake event broadcaster that doesn't do anything."}
  silent-event-brodcaster
  (reify wnebp/TxEventBroadcaster
    (message-persisted? [this changes]
      true)
    (send-message [this changes]
      nil)))

(defn db-lifecycle [f]
  (let [uri (str "datomic:mem://" *ns* "-"
                 (jt/to-millis-from-epoch (jt/instant)))]
    (let [conn (fixture-conn)
          tx-reqort-queue (d/tx-report-queue conn)
          monitor (partial wneb/start-queue-monitor
                           conn
                           silent-event-brodcaster)]
      (mount/start-with {#'wdb/conn conn
                         #'wneb/change-queue-monitor (monitor)})
      (f)
      (wdb/checked-delete uri)
      (mount/stop))))

(defn speculate [conn tx]
  (:db-after (d/with (d/db conn) tx)))
