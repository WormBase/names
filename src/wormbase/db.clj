(ns wormbase.db
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.walk :as w]
   [datomic.api :as d]
   [environ.core :as environ]
   [mount.core :as mount]
   [wormbase.util :as wu]
   [wormbase.db.schema :as db-schema]))

(def ^:dynamic *wb-db-uri* nil)

(def ^{:dynamic true
       :doc "Incremetn this number each time a schema change is made"}
  schema-version 1)

(defn connect
  "Connects to the datomic database and transacts schema if required."
  [uri]
  (let [conn (d/connect uri)]
    (db-schema/install conn)
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
             :allowed-uri-prefixes allowed-uri-prefixes
             :type :connection-error}))))

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

;; factoring out so can be mocked in tests.

(defn db
  [conn]
  (d/db conn))

(defn connection []
  conn)

;; end factoring

(defn wrap-datomic
  "Annotates request with datomic connection and current db."
  [request-handler]
  (fn [request]
    (when-not (connected?)
      (mount/start))
    (let [cx (connection)]
      (-> request
          (assoc :conn cx :db (db cx))
          (request-handler)))))

(defn invert-tx
  ([log tx provenance fact-mapper]
   (let [t (d/tx->t tx)]
     (if-let [datoms (some-> (d/tx-range log t (inc t)) first :data)]
       (transduce
        (comp
         (remove (fn [[e _ _ tx _]]
                   (= e tx)))
         (map (fn [[e a v tx added?]]
                (if-let [fact (fact-mapper e a v tx added?)]
                  fact
                  [(if added? :db/retract :db/add) e a v]))))
        conj
        [provenance]
        datoms)
       (throw (ex-info "No tx to invert"
                       {:tx tx
                        :type ::invert-tx-problem
                        :range (d/tx-range log t (inc t))})))))
    ([log tx provenance]
     (invert-tx log tx provenance (constantly nil))))

(defn extract-id [tx-result identity-kw]
  (some->> (:tx-data tx-result)
           (map :e)
           (map (partial d/entity (:db-after tx-result)))
           (map identity-kw)
           (filter identity)
           (set)
           (vec)
           (first)))

(defn ident-exists? [db ident]
  (pos-int? (d/entid db ident)))

(defn edn-definition [edn-name ident]
  (let [ident-match? #(= (:db/ident %) ident)]
    (->> (str "schema/" edn-name ".edn")
         (io/resource)
         (wu/read-edn)
         (filter ident-match?)
         (first))))

;; convenience aliases to read schema items from resources by keyword.

(def seed-data-schema (partial edn-definition "seed-data"))

(def attr-schema (partial edn-definition "definitions"))

(def txfn-schema (partial edn-definition "tx-fns"))

(defn pull [db expr & args]
  (let [format-result (comp (partial wu/elide-db-internals db) wu/elide-importer-info)]
    (format-result (apply d/pull db expr args))))
