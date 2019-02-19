(ns wormbase.names.recent
  (:require
   [buddy.core.codecs :as codecs]
   [buddy.core.codecs.base64 :as b64]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [java-time :as jt]
   [ring.util.http-response :refer [header ok]]
   [wormbase.db :as wdb]
   [wormbase.names.auth :as wna]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.recent :as wsr]
   [wormbase.util :as wu]))

(def conf (:recent (wnu/read-app-config)))

(def ^:dynamic *n-days-back* (:days-back conf))

(def ^:dynamic *etag-key* (:etag-key conf))

(defn activities
  "Return recent activities for both batch and individual operations.
  The result should be map whose keys represent these two groupings.
  The groupings in turn should be a sequence of maps."
  ([db log rules]
   (activities db log rules ""))
  ([db log rules needle]
   (activities db log rules needle *n-days-back*))
  ([db log rules needle n-days]
   (let [now (jt/to-java-date (jt/instant))
         since-t (-> (jt/instant)
                     (jt/minus (jt/days n-days))
                     (jt/to-java-date))
         since-db (d/since db since-t)
         hdb (d/history since-db)
         query '[:find [?tx ...]
                 :in $ $hdb ?log ?offset ?now ?needle %
                 :where
                 [(tx-ids ?log ?offset ?now) [e?tx ...]]
                 [$hdb ?tx ?a ?v]
                 [$ ?a :db/ident ?aname]
                 ($ filter-events ?tx ?aname ?v ?needle)]]
     (map (partial d/pull since-db wnp/pull-expr)
          (d/q query db hdb log since-t now needle rules)))))

(def entity-rules '[[(filter-events ?tx ?aname ?v ?needle)
                     [(missing? $ ?tx :batch/id)]
                     [?tx :provenance/what ?what]
                     [?what :db/ident ?what-kw]
                     [(name ?what-kw) ?what-name]
                     [(clojure.string/includes? ?what-name ?needle)]]])

(def person-rules '[[(filter-events ?tx ?aname ?v ?needle)
                     [?tx :provenance/who ?pid]
                     [?pid :person/email ?match-string]]])

(def batch-rules '[[(filter-events ?tx ?aname ?v ?needle)
                    [?tx :batch/id _ _ ]]])

(def response-schema (wnu/response-map ok {:schema {:activities ::wsr/activities}}))

(defn encode-etag [latest-t]
  (codecs/bytes->str (b64/encode (str latest-t))))

(defn decode-etag [etag]
  (codecs/bytes->str (b64/decode (codecs/str->bytes etag))))

(defn handle [request rules & [needle]]
  (let [{conn :conn db :db} request
        log (d/log conn)
        items (activities db log rules (or needle "") *n-days-back*)
        latest-t (some-> items first :db/txInstant)
        etag (encode-etag latest-t)
        _ (println "Latest-t:" latest-t)]
    (-> {:activities (map (partial wu/elide-db-internals db) items)}
        (ok)
        (header "etag" etag))))

(def routes (sweet/routes
             (sweet/context "/recent" []
               :tags ["recent"]
               ;; TODO: authenticated?
               ;; :middleware [wna/restrict-to-authenticated]
               (sweet/GET "/batch" request
                 :tags ["recent" "batch"]
                 :responses response-schema
                 (handle request batch-rules))
               (sweet/GET "/person" request
                 :tags ["person"]
                 :responses response-schema
                 (let [person-email (-> request :identity :person :person/email)]
                   (handle request person-rules person-email)))
               (sweet/GET "/gene" request
                 :tags ["recent" "gene"]
                 :responses response-schema
                 (handle request entity-rules "gene"))
               (sweet/GET "/variation" request
                 :tags ["variation"]
                 :responses response-schema
                 (handle request entity-rules "variation")))))

(comment
  "Examples of each invokation flavour"
  (in-ns 'wormbase.names.recent)

  Then define `conn` d/connect and db with d/db.

  (binding [*n-days-back** 43]
    (activities db (d/log conn) entity-rules "gene")
    (activities db (d/log conn) person-rules "matthew.rustsell@wormbase.org")
    (activities db (d/log conn) batch-rules)))
