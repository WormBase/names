(ns wormbase.names.entity
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.walk :as w]
   [datomic.api :as d]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [ring.util.http-response :refer [bad-request!
                                    conflict!
                                    conflict
                                    created not-found
                                    not-found!
                                    ok]]
   [spec-tools.core :as stc]
   [wormbase.db :as wdb]
   [wormbase.util :as wu]
   [wormbase.names.matching :as wnm]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.ids.core :as wbids]
   [wormbase.specs.common :as wsc]
   [wormbase.specs.entity :as wse]
   [wormbase.specs.provenance :as wsp]
   [wormbase.names.validation :refer [validate-names]]))

(def enabled-ident :wormbase.names/entity-type-enabled?)

(defmulti transform-ident-ref-value (fn [k m]
                                      k))

(defmethod transform-ident-ref-value :default [_ m]
  m)

(defmethod validate-names :default [request data]
  (when-not (some-> request :params :force)
    (let [db (:db request)
          ent-ns (-> request :params :entity-type)
          id-ident (keyword ent-ns "id")
          ident-ent (d/entity db id-ident)
          name-ident (keyword ent-ns "name")
          name-val (name-ident data)]
      (when (:wormbase.names/entity-type-enabled? ident-ent)
        (when (:wormbase.names/name-required? ident-ent)
          (when-not (and name-val (s/valid? ::wse/name name-val))
            [{:name "name"
              :value name-val}]))))))

(defn transform-ident-ref-values
  [data & {:keys [skip-keys]
           :or {skip-keys #{}}}]
  (reduce-kv (fn [m k _]
               (if (skip-keys k)
                 m
                 (transform-ident-ref-value k m)))
             data
             data))

(defn make-summary-pull-expr [entity-type]
  (let [pe '[*]
        attr-spec {(keyword entity-type "status") [:db/ident]}]
    (conj pe attr-spec)))

(defn identify
  "Return an lookup ref and entity for a given identifier.
  Lookups `identifier` (conformed with `identify-spec`) in the database.
  Returns `nil` when the entity cannot be found."
  [identitfy-spec ent-ns request identifier]
  (let [pair (s/conform identitfy-spec identifier)]
    (when (s/invalid? pair)
      (not-found! {:message "Malformed identifier"
                   :spec identitfy-spec
                   :data identifier}))
    (let [db (:db request)
          lookup-ref (if (-> pair first simple-keyword?)
                       [(-> (apply assoc {} pair)
                            (wnu/qualify-keys ent-ns)
                            (vec)
                            (ffirst))
                        (second pair)]
                       pair)
          ent (d/entity db lookup-ref)]
      (if (:db/id ent)
        [lookup-ref ent]
        (not-found! {:message "Entity lookup failed"
                     :lookup-ref lookup-ref})))))

(defn prepare-data-for-transact
  "Strip any data keys that are not valid datomic idents."
  [db data]
  (select-keys data (filter (partial d/entid db) (keys data))))

(defn handle-unnamed-or-named [data conformer]
  (let [[x conformed] (conformer data)]
    (if (= x :un-named)
      (dissoc conformed :n)
      conformed)))

(defn creator
  "Return an endpoint handler for new entity creation."
  [uiident conform-spec-fn event summary-pull-expr & [validate]]
  (fn handle-new [request]
    (let [{payload :body-params db :db conn :conn} request
          ent-ns (namespace uiident)
          live-status-attr (keyword ent-ns "status")
          live-status-val (keyword (str ent-ns ".status") "live")
          template (wbids/identifier-format db uiident)
          names-validator (if validate
                            (partial validate request)
                            identity)
          data (-> (:data payload)
                   (conform-spec-fn)
                   (wnu/qualify-keys ent-ns)
                   (transform-ident-ref-values)
                   (update live-status-attr (fnil identity live-status-val)))]
      (when-let [errors (names-validator data)]
        (throw (ex-info "One ore missing or invalid names found"
                        (merge {:type :user/validation-error}
                               errors))))
      (let [cdata (prepare-data-for-transact db (wnu/qualify-keys data ent-ns))
            prov (wnp/assoc-provenance request payload event)
            tx-data [['wormbase.ids.core/new template uiident [cdata]] prov]
            tx-res @(d/transact-async conn tx-data)
            dba (:db-after tx-res)]
        (when dba
          (let [new-id (wdb/extract-id tx-res uiident)
                emap (wdb/pull dba summary-pull-expr [uiident new-id])
                result {:created (wnu/unqualify-keys emap ent-ns)}]
            (created (str "/" ent-ns "/") result)))))))

(defn merge-into-ent-data
  [data ent-data db uiident]
  (let [uiident-ent (d/entity db uiident)]
    (merge (reduce-kv (fn [m k v]
                        (if (and (:wormbase.names/name-required? uiident-ent)
                                 (str/ends-with? (name k) "name"))
                          (dissoc m k v)
                          m))
                      ent-data
                      ent-data)
           data)))

(defn updater
  [identify-fn uiident conform-spec-fn event summary-pull-expr & [validate ref-resolver-fn]]
  (fn handle-update [request identifier]
    (let [{db :db conn :conn payload :body-params} request
          ent-ns (namespace uiident)
          uiident-ent (d/entity db uiident)
          [lur entity] (identify-fn request identifier)]
      (when entity
        (let [ent-data (wdb/pull db summary-pull-expr lur)
              names-validator (if validate
                                (partial validate request)
                                identity)
              data (-> payload
                       :data
                       (conform-spec-fn)
                       (wnu/qualify-keys ent-ns)
                       (merge-into-ent-data ent-data db uiident)
                       (transform-ident-ref-values))]
          (when-let [errors (names-validator data)]
            (throw (ex-info "One or more invalid names found."
                            {:type :user/validation-error
                             :errors errors})))
          (let [resolve-refs-to-db-ids (or ref-resolver-fn
                                           (fn noop-resolver [_ data]
                                             data))
               cdata (->> data
                          (resolve-refs-to-db-ids db)
                          (into {})
                          (prepare-data-for-transact db))
               prov (wnp/assoc-provenance request payload event)
               txes [['wormbase.ids.core/cas-batch lur cdata] prov]
               tx-result @(d/transact-async conn txes)]
          (when-let [db-after (:db-after tx-result)]
            (if-let [updated (wdb/pull db-after summary-pull-expr lur)]
              (ok {:updated (wnu/unqualify-keys updated ent-ns)})
              (not-found
               (format "%s '%s' does not exist" ent-ns (last lur)))))))))))

(defn status-changer
  "Return a handler to change the status of an entity to `to-status`.

  `identfiier` - A identifier string that must uniquely identify an entity.
  `to-status` - a keyword/ident identifying an entity status. (e.g: :gene.status/live)
  `event-type` - keyword/ident
  `fail-precondition?` - A function that takes a single argument of
                         the current entity status, and should return a truth-y value
                         to indicate if request processing should continue.
  `predcondition-failure-msg` - An optional message to send back in the
  case where fail-precondition? returns true."
  [uiident status-ident to-status event-type
   & {:keys [fail-precondition? precondition-failure-msg]
      :or {precondition-failure-msg "status cannot be updated."}}]
  (fn change-status
    [request identifier]
    (let [{conn :conn db :db payload :body-params} request
          lur [uiident identifier]
          pull-status #(wdb/pull % [{status-ident [:db/ident]}] lur)
          {status status-ident} (pull-status db)]
      (when (and status
                 fail-precondition?
                 (fail-precondition? status))
        (bad-request! {:message precondition-failure-msg
                       :info (wu/elide-db-internals db status)}))
      (let [prov (wnp/assoc-provenance request payload event-type)
            ent-ns (-> lur first namespace)
            tx-res @(d/transact-async
                     conn [[:db/cas
                            lur
                            status-ident
                            (d/entid db status)
                            (d/entid db to-status)]
                           prov])
            dba (:db-after tx-res)]
        (-> (pull-status dba)
            (wnu/unqualify-keys ent-ns)
            (update :status name)
            (ok))))))

(defn handle-kill [request entity-type identifier]
  (let [id-ident (keyword entity-type "id")
        status-ident (keyword entity-type "status")
        to-status (keyword (str entity-type ".status") "dead")
        event-ident (keyword "event" (str "kill-" entity-type))
        precond-failure-msg (format "%s to be killed is already dead."
                                    entity-type)
        kill (status-changer id-ident
                             status-ident
                             to-status
                             event-ident
                             :fail-precondition? wnu/dead?
                             :precondition-failure-msg precond-failure-msg)]
    (kill request identifier)))

(defn handle-resurrect [request entity-type identifier]
  (let [id-ident (keyword entity-type "id")
        status-ident (keyword entity-type "status")
        to-status-ident (keyword (str entity-type ".status") "live")
        event-ident (keyword "event" (str "resurrect-" entity-type))
        precond-fail-msg (format "%s is already live." entity-type)
        resurrect (status-changer id-ident
                                  status-ident
                                  to-status-ident
                                  event-ident
                                  :fail-precondition? wnu/live?
                                  :precondition-failure-msg precond-fail-msg)]
    (resurrect request identifier)))

(defn summarizer [identify-fn pull-expr ref-attrs]
  (fn handle-summary [request identifier]
    (let [{db :db conn :conn} request
          log (d/log conn)
          [lur ent] (identify-fn request identifier)]
      (when-not (and lur ent)
        (not-found! {:message "Unable to find any entity for given identifier."
                     :identifier identifier}))
      (let [ent-ns (-> lur first namespace)
            info (reduce-kv (fn unqalify-idents [m k v]
                              (cond
                                (qualified-keyword? v) (assoc m k (name v))
                                :default (assoc m k v)))
                            {}
                            (-> (wdb/pull db pull-expr lur)
                                (wnu/unqualify-keys ent-ns)))
            prov (map
                  (fn [prov-data]
                    (-> prov-data
                        (wnu/unqualify-keys "provenance")
                        (update :who (fn [who]
                                       (wnu/unqualify-keys who "person")))))
                  (wnp/query-provenance db log lur ref-attrs))]
        (-> info
            (assoc :history prov)
            (ok))))))

(defn finder [entity-type]
  (fn handle-find [request]
    (when-let [pattern (some-> request :query-params :pattern str/trim)]
      (let [db (:db request)
            term (stc/conform ::wsc/find-term pattern)
            name-ident (keyword entity-type "name")
            id-ident (keyword entity-type "id")
            ent-ns (namespace id-ident)
            q-spec '{:find [?id ?name]
                     :in [$ ?pattern ?id-ident ?name-ident]
                     :where [[(re-seq ?pattern ?name)]
                             [?e ?id-ident ?id]
                             [?e ?name-ident ?name]]}
            q-result (d/q (assoc q-spec :keys [id-ident name-ident])
                          db
                          (re-pattern (str "^" term))
                          id-ident
                          name-ident)
            res {:matches (if (seq q-result)
                            (map #(wnu/unqualify-keys % ent-ns) q-result)
                            [])}]
        (ok res)))))

(defn generic-attrs
  "Return the datomic attribute schema for a generic entity."
  [id-ident]
  (let [entity-type (namespace id-ident)
        status-ident-ns (str entity-type ".status")]
    [#:db{:ident id-ident
          :valueType :db.type/string
          :cardinality :db.cardinality/one
          :unique :db.unique/identity
          :doc (format "The primary identifier of the %s." entity-type)}
     #:db{:ident (keyword entity-type "name")
          :valueType :db.type/string
          :cardinality :db.cardinality/one
          :unique :db.unique/value
          :doc (format "The primary name of the %s." entity-type)}
     #:db{:ident (keyword entity-type "status")
          :valueType :db.type/ref
          :cardinality :db.cardinality/one
          :doc (format "The status of the %s." entity-type)}
     #:db{:ident (keyword status-ident-ns "dead")}
     #:db{:ident (keyword status-ident-ns "live")}
     #:db{:ident (keyword status-ident-ns "suppressed")}
     #:db{:ident (keyword "event" (str "new-" entity-type))}]))

(defn entity-schema-registered? [conn id-ident]
  (when-let [ent (d/entity (d/db conn) id-ident)]
    (:wormbase.names/entity-type-enabled? ent)))

(defn register-entity-schema
  "Register a new datomic entity schema."
  ([conn id-ident id-template prov generic? enabled? name-required?]
   (let [attrs-tx-data (conj (generic-attrs id-ident) prov)
         tx-res @(d/transact-async conn attrs-tx-data)
         temp-id (str id-ident)
         reg-tx-data [[:db/add id-ident :wormbase.names/id-template-format id-template]
                      [:db/add id-ident :wormbase.names/entity-type-generic? generic?]
                      [:db/add id-ident :wormbase.names/entity-type-enabled? enabled?]
                      [:db/add id-ident :wormbase.names/name-required? name-required?]]]
     (when (:db-after tx-res)
       @(d/transact-async conn reg-tx-data)
       attrs-tx-data)))
  ([conn id-ident id-template prov]
   (register-entity-schema conn id-ident id-template prov true true true)))

(defn handle-register-entity-schema
  [request]
  (let [{db :db conn :conn payload :body-params} request
        {data :data} payload
        {entity-type :entity-type
         id-template :id-template
         generic? :generic
         name-required? :name-required} data
        id-attr (keyword entity-type "id")
        prov (wnp/assoc-provenance request payload :event/new-entity-type)]
    (when-not entity-type
      (bad-request! {:message "entity-type not provided"}))
    (when (d/entity db id-attr)
      (conflict! {:message (format "Schema %s already exists! (:%s/id)"
                                   entity-type
                                   entity-type)}))
    (when (register-entity-schema conn
                                  id-attr
                                  id-template
                                  prov
                                  generic?
                                  true
                                  name-required?)
      (created (str "/api/" entity-type)
               {:message (format "Created generic schema for %s"
                                 entity-type)}))))

(defn list-entity-schemas [request]
  (let [entity-types (->> (d/q '[:find ?entity-type ?generic ?enabled ?named
                                 :keys entity-type generic? enabled? named?
                                 :in $
                                 :where
                                 [?et :wormbase.names/entity-type-enabled? ?enabled]
                                 [?et :wormbase.names/entity-type-generic? ?generic]
                                 [?et :wormbase.names/name-required? ?named]
                                 [?et :db/ident ?entity-type]]
                               (:db request))
                          (map #(update % :entity-type namespace))
                          (map #(update % :enabled? (fnil identity true)))
                          (sort-by :entity-type)
                          (into []))]
    (ok {:entity-types entity-types})))

(defn check-enabled! [request entity-type test-fn err-msgs]
  (let [{db :db conn :conn} request
        ent-type-ident (keyword entity-type "id")
        {curr-enabled? enabled-ident et-ident :db/ident} (d/pull db '[*] ent-type-ident)]
    (when-not et-ident
      (not-found! (-> err-msgs
                      (select-keys [:not-found-message])
                      (assoc :entity-type entity-type))))
    (when (and (test-fn curr-enabled?)
               (:conflict-message err-msgs)
               (not (::ignore-enabled-conflict request)))
      (conflict! (-> err-msgs
                     (select-keys [:conflict-message])
                     (assoc :entity-type entity-type))))
    {:ident ent-type-ident :enabled curr-enabled?}))

(defn enabled-handler
  [request ent-type-ident curr-enabled? enable? event-ident]
  (let [prov (wnp/assoc-provenance request {} event-ident)
        txes [[:db/cas ent-type-ident enabled-ident curr-enabled? enable?] prov]
        tx-res @(d/transact-async (:conn request) txes)]
    (when (:db-after tx-res)
      (ok {:message (format "Entity type %s was %s."
                            (namespace ent-type-ident)
                            (if enable?
                              "enabled"
                              "disabled"))}))))

(defn disable-entity-type [request entity-type]
  (let [{:keys [ident enabled]} (check-enabled!
                                 request
                                 entity-type
                                 false?
                                 {:not-found-message "Entity type is not installed."})]
    (enabled-handler request ident enabled false :event/disable-entity-type)))

(defn enable-entity-type [request entity-type]
  (let [{:keys [ident enabled]} (check-enabled!
                                 (assoc request ::skip-enabled-mw-check true)
                                 entity-type
                                 true?
                                 {:not-found-message "Entity type is already installed."})]
    (enabled-handler request ident enabled true :event/enable-entity-type)))

(defn entity-enabled-checker
  "Middleware to check if an entity is enabled given information in the URL."
  [handler]
  (fn enabled-checker [request]
    (check-enabled! (assoc request ::ignore-enabled-conflict true)
                    (-> request :params :entity-type)
                    false?
                    {:not-found-message "Entity type not installed."
                     :conflict-message "Entity type has been disabled and cannot be used."})
    (handler request)))

(def status-changed-responses
  (-> wnu/default-responses
      (assoc ok {:schema ::wse/status-changed})
      (wnu/response-map)))

(def coll-resources
  (sweet/context "/entity" []
    :tags ["entity"]
    (sweet/resource
     {:get
      {:summary "List all simple entity types."
       :responses (wnu/http-responses-for-read
                   {:schema {:entity-types ::wse/schema-listing}})
       :handler (fn handle-list-entity-schemas [request]
                  (list-entity-schemas request))}
      :post
      {:summary "Add a new simple entity type to the system."
       :responses (wnu/response-map created {:schema ::wse/schema-created})
       :parameters {:body-params {:data ::wse/new-schema
                                  :prov ::wsp/provenance}}
       :handler (fn register-entity-schema [request]
                  (handle-register-entity-schema request))}})))

(def item-resources
  (sweet/context "/entity/:entity-type" []
    :tags ["entity"]
    :path-params [entity-type :- ::wse/entity-type]
    (sweet/resource
     {:delete
      {:summary "Mark an entity type as disabled."
       :x-name ::disable-entity-type
       :responses (wnu/response-map wnu/default-responses)
       :parameters {:body-params {:prov ::wsp/provenance}}
       :handler (fn handle-disable-ent-type [request]
                  (disable-entity-type request entity-type))}
      :get
      {:summary>> "Find variations by any unique identifier."
       :responses (wnu/http-responses-for-read {:schema ::wsc/find-response})
       :parameters {:query-params ::wsc/find-request}
       :x-name ::find-entities
       :handler (finder entity-type)}
      :put
      {:summary "Update the schema for an entity type (enable/disable only)."
       :x-name ::enable-entity-type
       :parameters {:body-params {:prov ::wsp/provenance}}
       :responses (wnu/response-map wnu/default-responses)
       :handler (fn handle-enable-entity-type [request]
                  (enable-entity-type request entity-type))}
      :post
      {:summary "Create a new entity."
       :x-name ::new-entity
       :parameters {:body-params {:data ::wse/new
                                  :prov ::wsp/provenance}}
       :responses (-> wnu/default-responses
                      (assoc created {:schema {:created ::wse/created}})
                      (wnu/response-map))
       :handler (fn new-entity [request]
                  (let [id-ident (keyword entity-type "id")
                        event-ident (keyword "event" (str "new-" entity-type))
                        conformer (partial wnu/conform-data ::wse/new)
                        spe (make-summary-pull-expr entity-type)
                        new-entity (creator id-ident
                                            conformer
                                            event-ident
                                            spe
                                            validate-names)]
                    (new-entity request)))}})
    (sweet/context "/:identifier" []
      :tags ["entity"]
      :middleware [entity-enabled-checker]
      :path-params [identifier :- ::wse/identifier]
      (sweet/resource
       {:delete
        {:summary "Kill an entity."
         :x-name ::kill-entity
         :parameters {:body-params {:prov ::wsp/provenance}}
         :responses status-changed-responses
         :handler (fn [request]
                    (handle-kill request entity-type identifier))}
        :put
        {:summary "Update an existing entity."
         :x-name ::update-entity
         :parameters {:body-params {:data ::wse/update
                                    :prov ::wsp/provenance}}
         :responses (-> wnu/default-responses
                        (dissoc conflict)
                        (wnu/response-map))
         :handler (fn handle-update [request]
                    (let [id-ident (keyword entity-type "id")
                          update-spec ::wse/update
                          event-ident (keyword "event" (str "update-" entity-type))
                          summary-pull-expr (make-summary-pull-expr entity-type)
                          update-handler (updater (partial identify ::wse/identifier entity-type)
                                                  id-ident
                                                  (partial wnu/conform-data update-spec)
                                                  event-ident
                                                  summary-pull-expr
                                                  validate-names)]
                      (update-handler request identifier)))}
        :get
        {:summary "Summarise an entity."
         :x-name ::about-entity
         :responses (wnu/http-responses-for-read {:schema ::wse/summary})
         :handler (fn handle-entity-summary [request]
                    (let [summary-pull-expr (make-summary-pull-expr entity-type)
                          summarize (summarizer (partial identify ::wse/identifier entity-type)
                                                summary-pull-expr
                                                #{})]
                      (summarize request identifier)))}})
      (sweet/context "/resurrect" []
        (sweet/resource
         {:post
          {:summary "Resurrect an entity."
           :x-name ::resurrect-entity
           :respones status-changed-responses
           :handler (fn handle-resurrect-entity [request]
                      (handle-resurrect request entity-type identifier))}})))))

(def routes (sweet/routes coll-resources
                          item-resources))
