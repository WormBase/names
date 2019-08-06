(ns wormbase.names.recent
  (:require
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [java-time :as jt]
   [ring.middleware.not-modified :as rmnm]
   [ring.util.http-response :refer [ok]]
   [wormbase.db :as wdb]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.recent :as wsr]
   [wormbase.util :as wu])
  (:import (java.util Date)))

(def conf (:recent (wnu/read-app-config)))

(defn- find-max-imported-date [db]
  (let [max-tx-inst (d/q '[:find (max ?inst) .
                           :where
                           [?tx :provenance/how :agent/importer]
                           [?tx :db/txInstant ?inst]]
                         db)
        max-date (if max-tx-inst
                   (jt/instant max-tx-inst)
                   (jt/instant))]
    (-> max-date
        (jt/plus (jt/seconds 1))
        (jt/to-java-date))))

(def imported-date (memoize find-max-imported-date))

(def query '[:find ?tx ?e
             :in $ % ?log ?start ?end ?needle [?how ...]
             :where
             [(tx-ids ?log ?start ?end) [?tx ...]]
             [?tx :provenance/how ?how]
             (filter-events ?tx ?needle)
             [(tx-data ?log ?tx) [[?e]]]])

(defn query-activities
  ([db log rules ^Date from ^Date until how]
   (query-activities db log rules "" from until how))
  ([db log rules needle ^Date from ^Date until how]
   ;; find the date for the most recent transaction after imported transactions.
   (let [import-date (imported-date db)
         ;; choose the date that is older betweem thn requested date and last import tx
         from-t (if (and from (>= (compare from import-date) 0))
                  from
                  import-date)
         until-t (or until (jt/to-java-date (jt/instant)))]
         ;; Timings for the `tx-ids` query below with default configured time window (60 days)
         ;; (excluding pull expressions)
         ;; jvm (cold): 107.266427 msecs
     ;; jvm (warm): 30.054339 msecs
     (d/q query db rules log from-t until-t needle how))))

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

(defn sort-activities [acts]
  (wu/sort-events-by :t acts :most-recent-first true))

(defn activities
  "Return recent activities for both batch and individual operations.
  The result should be map whose keys represent these two groupings.
  The groupings in turn should be a sequence of maps."
  [db log rules puller needle how ^Date from ^Date until]
  (some->> (query-activities db log rules needle from until how)
           (sequence puller)
           (remove nil?)
           (map (partial wu/elide-db-internals db))
           (sort-activities)))

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

(defn handle
  ([request rules puller needle from until]
   (handle request rules puller needle from until #{:agent/console :agent/web}))
  ([request rules puller needle from until how]
   (let [{conn :conn db :db} request
         log (d/log conn)
         from* (or from (wu/days-ago wsr/*default-days-ago*))
         until* (or until (jt/to-java-date (jt/instant)))
         items (activities db log rules puller (or needle "") how from* until*)
         etag (some-> items first :t wnu/encode-etag)]
     (some-> {:from from* :until until*}
             (assoc :activities (reverse items))
             (ok)
             (wnu/add-etag-header-maybe etag)))))

(def routes (sweet/routes
             (sweet/context "/recent" []
               :tags ["recent"]
               :responses {200 {:schema {:activities ::wsr/activities}}}
               :query-params [{from :- ::wsr/from nil}
                              {until :- ::wsr/until nil}]
               :middleware [rmnm/wrap-not-modified]
               (sweet/GET "/batch" request
                 :tags ["recent" "batch"]
                 :summary "List recent batch activity."
                 (handle request batch-rules (prov-only-puller request) "" from until))
               (sweet/GET "/person/:id" request
                 :tags ["recent" "person"]
                 :path-params [id :- :person/id]
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
                 :query-params [{how :- [::wsr/how] #{:agent/console :agent/web}}]
                 (handle request
                         entity-rules
                         (changes-and-prov-puller request)
                         "gene"
                         from
                         until
                         how))
               (sweet/GET "/gene/:agent" request
                 :tags ["recent" "gene"]
                 :summary "List recent gene activity performed via a given agent."
                 :path-params [agent :- ::wsr/agent]
                 (handle request
                         entity-rules
                         (changes-and-prov-puller request)
                         "gene"
                         from
                         until
                         #{(keyword "agent" agent)}))
               (sweet/GET "/variation" request
                 :tags ["recent" "variation"]
                 :summary "List recent variation activity."
                 (handle request
                         entity-rules
                         (changes-and-prov-puller request)
                         "variation"
                         from
                         until
                         #{:agent/console :agent/web}))
               (sweet/GET "/variation/:agent" request
                 :tags ["recent" "variation"]
                 :summary "List recent variation activity perfomed via a given agent."
                 :path-params [agent :- ::wsr/agent]
                 (handle request
                         entity-rules
                         (changes-and-prov-puller request)
                         "variation"
                         from
                         until
                         #{(keyword "agent" agent)})))))

(comment
  "Examples of each invokation flavour"
  (in-ns 'wormbase.names.recent)

  ;; Then define `conn` d/connect and db with d/db.
  (binding [db (d/db conn) log (d/log conn)
            pull-prov-only (prov-only-puller db log)
            pull-changes-and-prov (changes-and-prov-puller db log)
            from (jt/to-java-date (jt/instant))
            until (wu/days-ago 2)]
    (activities db log entity-rules pull-changes-and-prov "gene")
    (activities db log entity-rules-rules pull-changes-and-prov "gene" from until)

    (activities db log entity-rules pull-changes-and-prov "variation")
    (activities db log entity-rules-rules pull-changes-and-prov "variation" from until)

    (activities db (d/log conn) person-rules pull-changes-and-prov "matthew.russell@wormbase.org")
    (activities db (d/log conn) person-rules pull-changes-and-prov "matthew.rustsell@wormbase.org" from until)

    (activities db (d/log conn) batch-rules pull-prov-only)
    (activities db (d/log conn) batch-rules pull-prov-only from until)))
