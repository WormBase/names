(ns wormbase.db-testing
  (:require
   [clojure.core.cache :as cache]
   [datomock.core :as dm]
   [datomic.api :as d]
   [java-time.api :as jt]
   [mount.core :as mount]
   [wormbase.db :as wdb]
   [wormbase.db.schema :as schema]))

;;; fixture caching and general approach taken verbatim from:
;;; https://vvvvalvalval.github.io/posts/2016-07-24-datomic-web-app-a-practical-guide.html

(defn make-fixture-conn
  []
  (let [conn (dm/mock-conn)]
    (schema/ensure-schema conn)
    ;; A set of fake users
    @(d/transact-async conn [{:person/email "tester@wormbase.org"
                              :person/id "WBPerson007"}
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

(defn db-lifecycle [f]
  (let [uri (str "datomic:mem://" *ns* "-"
                 (jt/to-millis-from-epoch (jt/instant)))
        conn (fixture-conn)]
    (mount/start-with {#'wdb/conn conn})
    (f)
    (wdb/checked-delete uri)
    (mount/stop)))
