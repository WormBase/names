(ns wormbase.names.recent
  (:require
   [buddy.core.codecs :as codecs]
   [buddy.core.codecs.base64 :as b64]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [java-time :as jt]
   [ring.middleware.not-modified :as rmnm]
   [ring.util.http-response :refer [header ok]]
   [wormbase.db :as wdb]
   [wormbase.names.auth :as wna]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.recent :as wsr]
   [wormbase.util :as wu])
  (:import (java.util Date)))

(def conf (:recent (wnu/read-app-config)))

(def ^{:dynamic true} *default-days-ago* 60)

(defn since-days-ago [n]
  (-> (jt/instant)
      (jt/minus (jt/days n))
      (jt/to-java-date)))

(defn- find-max-imported-date [db]
  (-> (d/q '[:find (max ?inst) .
             :where
             [?tx :provenance/how :agent/importer]
             [?tx :db/txInstant ?inst]]
           db)
      (jt/instant)
      (jt/plus (jt/seconds 1))
      (jt/to-java-date)))

(def imported-date (memoize find-max-imported-date))

(defn query-activities
  ([db log rules ^Date from ^Date until]
   (query-activities db log rules "" from until))
  ([db log rules needle ^Date from ^Date until]
   ;; find the date for the most recent transaction after imported transactions.
   (let [import-date (imported-date db)
         ;; choose the date that is older betweem thn requested date and last import tx
         since-t (if (and from (>= (compare from import-date) 0))
                   from
                   import-date)
         now (jt/to-java-date (jt/instant))
         ;; Timings for the `tx-ids` query below with default configured time window (60 days)
         ;; (excluding pull expressions)
         ;; jvm (cold): 107.266427 msecs
         ;; jvm (warm): 30.054339 msecs
         query '[:find ?tx ?e
                 :in $ % ?log ?since-start ?since-end ?needle
                 :where
                 [(tx-ids ?log ?since-start ?since-end) [?tx ...]]
                 (filter-events ?tx ?needle)
                 [(tx-data ?log ?tx) [[?e]]]]]
     (d/q query db rules log since-t now needle))))

(defn changes-and-prov-puller
  "Creates a transducer for pulling changes and provenance."
  [request]
  (map (fn [[tx-id ent-id]]
         (when-not (= tx-id ent-id)
           (let [{db :db conn :conn} request
                 log (d/log conn)
                 pull-changes (partial wnp/query-tx-changes-for-event db log ent-id)
                 ent (d/entity db ent-id)
                 ident (some->> (keys ent)
                                (filter (fn [k]
                                          (= (name k) "id")))
                                (first))]
             (merge (wnp/pull-provenance db ent-id wnp/pull-expr tx-id pull-changes)
                    (when ident
                      (find ent ident))))))))

(defn prov-only-puller
  [request]
  (map (fn [[tx-id ent-id]]
         (when-not (= tx-id ent-id)
           (wnp/pull-provenance (:db request) ent-id wnp/pull-expr tx-id)))))

(defn activities
  "Return recent activities for both batch and individual operations.
  The result should be map whose keys represent these two groupings.
  The groupings in turn should be a sequence of maps."
  ([db log rules puller ^Date from ^Date until]
   (activities db log rules puller "" from until))
  ([db log rules puller needle ^Date from ^Date until]
   (->> (query-activities db log rules from until)
        (sequence puller)
        (remove nil?))))

(def entity-rules '[[(filter-events ?tx ?needle)
                     [(missing? $ ?tx :batch/id)]
                     [?tx :provenance/what ?wid]
                     [?wid :db/ident ?what]
                     [(name ?what) ?w]
                     [(clojure.string/includes? ?w ?needle)]]])

(def person-rules '[[(filter-events ?tx ?needle)
                     [?tx :provenance/who ?pid]
                     [?pid :person/email ?needle]]])

(def batch-rules '[[(filter-events ?tx ?needle)
                    [?tx :batch/id _ _ ]]])


(def response-schema (wnu/response-map ok {:schema {:activities ::wsr/activities}}))

(defn encode-etag [latest-t]
  (-> latest-t str b64/encode codecs/bytes->str))

(defn decode-etag [etag]
  (-> etag codecs/str->bytes b64/decode codecs/bytes->str))

(defn handle [request rules puller & [needle from until]]
  (let [{conn :conn db :db} request
        log (d/log conn)
        from* (or from (since-days-ago *default-days-ago*))
        until* (or until (jt/to-java-date (jt/instant)))
        items (->> (activities db log rules puller (or needle "") from* until*)
                   (map (partial wu/elide-db-internals db))
                   (sort-by :t))
        latest-t (some-> items first :t)
        etag (encode-etag latest-t)]
    (-> {:activities items}
        (ok)
        (header "etag" etag))))

(def routes (sweet/routes
             (sweet/context "/recent" []
               :tags ["recent"]
               :responses {200 {:schema {:activities ::wsr/activities}}}
               :query-params [{from :- ::wsr/from nil}
                              {util :- ::wsr/until nil}]
               :middleware [wna/restrict-to-authenticated
                            rmnm/wrap-not-modified]
               (sweet/GET "/batch" request
                 :tags ["recent" "batch"]
                 :summary "List recent batch activity."
                 (handle request batch-rules (prov-only-puller request)))
               (sweet/GET "/person" request
                 :tags ["recent" "person"]
                 :summary "List recent activities made by the currently logged-in user."
                 (let [person-email (-> request :identity :person :person/email)]
                   (handle request person-rules (changes-and-prov-puller request) person-email)))
               (sweet/GET "/gene" request
                 :tags ["recent" "gene"]
                 :summary "List recent gene activity."
                 (handle request entity-rules (changes-and-prov-puller request) "gene"))
               (sweet/GET "/variation" request
                 :tags ["recent" "variation"]
                 :summary "List recent variation activity."
                 (handle request entity-rules (changes-and-prov-puller request) "variation")))))

(comment
  "Examples of each invokation flavour"
  (in-ns 'wormbase.names.recent)

  Then define `conn` d/connect and db with d/db.
  (binding [from (jt/to-java-date (jt/instant))
            until (since-days-ago 2)]
    (activities db (d/log conn) entity-rules "gene")
    (activities db (d/log conn) entity-rules "gene" from until)
    (activities db (d/log conn) person-rules "matthew.rustsell@wormbase.org")
    (activities db (d/log conn) person-rules "matthew.rustsell@wormbase.org" from until)
    (activities db (d/log conn) batch-rules)
    (activities db (d/log conn) batch-rules from until)))
