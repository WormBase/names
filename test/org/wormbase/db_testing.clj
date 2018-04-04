(ns org.wormbase.db-testing
  (:require
   [clojure.core.cache :as cache]
   [datomock.core :as dm]
   [datomic.api :as d]
   [java-time :as jt]   
   [mount.core :as mount]
   [org.wormbase.db :as owdb]
   [org.wormbase.db.schema :as schema]
   [org.wormbase.names.event-broadcast :as own-eb]))

;;; fixture caching and general approach taken verbatim from:
;;; https://vvvvalvalval.github.io/posts/2016-07-24-datomic-web-app-a-practical-guide.html

(defn make-fixture-conn
  []
  (let [conn (dm/mock-conn)]
    (schema/install conn 1)
    ;; A set of fake users with different roles to test against.
    @(d/transact conn [{:person/email "tester@wormbase.org"
                        :person/id "WBPerson007"
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

(defn send-changes-test [changes & {:keys [verbose]
                                    :or {verbose false}}]
  (print "FAKING SENDING CHANGES FOR GENEACE CONSUMPTION")
  (if verbose
    (prn ":" changes)
    (println)))
  
(defn db-lifecycle [f]
  (let [uri (str "datomic:mem://" *ns* "-"
                 (jt/to-millis-from-epoch (jt/instant)))]
    (let [conn (fixture-conn)
          tx-reqort-queue (d/tx-report-queue conn)
          monitor (partial own-eb/start-queue-monitor conn send-changes-test)]
      (mount/start-with {#'owdb/conn conn
                         #'own-eb/change-queue-monitor (monitor)})
      (f)
      (owdb/checked-delete uri)
      (mount/stop))))

(defn speculate [conn tx]
  (:db-after (d/with (d/db conn) tx)))

