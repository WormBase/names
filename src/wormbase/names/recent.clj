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
  ([db log rules ^Date from ^Date until how]
   (query-activities db log rules "" from until how))
  ([db log rules needle ^Date from ^Date until how]
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
                 :in $ % ?log ?since-start ?since-end ?needle ?how
                 :where
                 [(tx-ids ?log ?since-start ?since-end) [?tx ...]]
                 (filter-events ?tx ?needle ?how)
                 [(tx-data ?log ?tx) [[?e]]]]]
     (d/q query db rules log since-t now needle how))))

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
  [db log rules puller needle how ^Date from ^Date until]
  (->> (query-activities db log rules from until how)
       (sequence puller)
       (remove nil?)))

(def entity-rules '[[(filter-events ?tx ?needle ?how)
                     [(missing? $ ?tx :batch/id)]
                     [?tx :provenance/what ?wid]
                     [?tx :provenance/how ?how]
                     [?wid :db/ident ?what]
                     [(name ?what) ?w]
                     [(clojure.string/includes? ?w ?needle)]]])

(def person-rules '[[(filter-events ?tx ?needle _)
                     [?tx :provenance/who ?pid]
                     [?pid :person/email ?needle]]])

(def batch-rules '[[(filter-events ?tx ?needle _)
                    [?tx :batch/id _ _ ]]])


(def response-schema (wnu/response-map ok {:schema {:activities ::wsr/activities}}))

(defn encode-etag [latest-t]
  (-> latest-t str b64/encode codecs/bytes->str))

(defn decode-etag [etag]
  (-> etag codecs/str->bytes b64/decode codecs/bytes->str))

(defn handle
  ([request rules puller needle from until]
   (handle request rules puller needle #{:agent/console :agent/web} from until))
  ([request rules puller needle how from until]
   (let [{conn :conn db :db} request
         log (d/log conn)
         from* (or from (since-days-ago *default-days-ago*))
         until* (or until (jt/to-java-date (jt/instant)))
         items (->> (activities db log rules puller (or needle "") how from* until*)
                    (map (partial wu/elide-db-internals db))
                    (sort-by :t))
         latest-t (some-> items first :t)
         etag (encode-etag latest-t)]
     (-> {:activities items}
         (ok)
         (header "etag" etag)))))

(def routes (sweet/routes
             (sweet/context "/recent" []
               :tags ["recent"]
               :responses {200 {:schema {:activities ::wsr/activities}}}
               :query-params [{from :- ::wsr/from nil}
                              {until :- ::wsr/until nil}]
               :middleware [wna/restrict-to-authenticated
                            rmnm/wrap-not-modified]
               (sweet/GET "/batch" request
                 :tags ["recent" "batch"]
                 :summary "List recent batch activity."
                 (handle request batch-rules (prov-only-puller request) "" from until))
               (sweet/GET "/person" request
                 :tags ["recent" "person"]
                 :query-params [id :- :person/id]
                 :summary "List recent activities made by the currently logged-in user."
                 (when-let [person (if id
                                     (d/pull (:db request) [:person/email] [:person/id id])
                                     (some-> request :identity :person))]
                   (handle request
                           person-rules
                           (changes-and-prov-puller request)
                           (:person/email person)
                           from
                           until)))
               (sweet/GET "/gene" request
                 :tags ["recent" "gene"]
                 :summary "List recent gene activity."
                 :query-params [how :- ::wsr/how]
                 (do
                   (println "HOW:" how)
                   (handle request
                           entity-rules
                           (changes-and-prov-puller request)
                           "gene"
                           how
                           from
                           until)))
               (sweet/GET "/variation" request
                 :tags ["recent" "variation"]
                 :summary "List recent variation activity."
                 :query-params [how :- ::wsr/how]
                 (handle request
                         entity-rules
                         (changes-and-prov-puller request)
                         "variation"
                         how
                         from
                         until)))))

(comment
  "Examples of each invokation flavour"
  (in-ns 'wormbase.names.recent)

  Then define `conn` d/connect and db with d/db.
  (binding [db (d/db conn) log (d/log conn)
            pull-prov-only (prov-only-puller db log)
            pull-changes-and-prov (changes-and-prov-puller db log)
            from (jt/to-java-date (jt/instant))
            until (since-days-ago 2)]
    (activities db log entity-rules pull-changes-and-prov "gene")
    (activities db log entity-rules-rules pull-changes-and-prov "gene" from until)

    (activities db log entity-rules pull-changes-and-prov "variation")
    (activities db log entity-rules-rules pull-changes-and-prov "variation" from until)

    (activities db (d/log conn) person-rules pull-changes-and-prov "matthew.rustsell@wormbase.org")
    (activities db (d/log conn) person-rules pull-changes-and-prov "matthew.rustsell@wormbase.org" from until)
    
    (activities db (d/log conn) batch-rules pull-prov-only)
    (activities db (d/log conn) batch-rules pull-prov-only from until)))
