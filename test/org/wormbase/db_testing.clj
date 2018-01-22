(ns org.wormbase.db-testing
  (:require
   [clojure.string :as str]
   [clojure.test :as t]
   [clojure.core.cache :as cache]
   [clj-time.coerce :as ctc]
   [clj-time.core :as ct]
   [clj-time.core :as ct]
   [datomock.core :as dm]
   [datomic.api :as d]
   [mount.core :as mount]
   [org.wormbase.db :as owdb]
   [org.wormbase.db.schema :as schema]))

;;; fixture caching and general approach taken verbatim from:
;;; https://vvvvalvalval.github.io/posts/2016-07-24-datomic-web-app-a-practical-guide.html

(defn make-fixture-conn
  []
  (let [conn (dm/mock-conn)]
    (schema/install conn 1)
    ;; A set of fake users with different roles to test against.
    @(d/transact conn [{:user/email "tester@wormbase.org"
                        :user/roles #{:user.role/admin}}
                       {:user/email "tester2@wormbase.org"}
                       {:user/email "tester3@wormbase.org"}])
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
  (let [uri (str "datomic:mem://" *ns* "-" (ctc/to-long (ct/now)))]
    (let [conn (fixture-conn)]
      (mount/start-with {#'owdb/conn conn})
      (f)
      (owdb/checked-delete uri)
      (mount/stop))))

(defn empty-db []
  (let [conn (fixture-conn)]
    (schema/install conn 1)
    ;; (let [test-user-email "tester@wormbase.org"]
    ;;   (when-not (d/q '[:find ?e
    ;;                    :in $ ?email
    ;;                    :where [?e :user/email ?email]]
    ;;                  (d/db conn) test-user-email)
    ;;     @(d/transact conn [{:user/email test-user-email}])))
    (d/db conn)))

(defn speculate [tx]
  (:db-after (d/with (empty-db) tx)))

