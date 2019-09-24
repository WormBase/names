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
   [wormbase.specs.provenance :as wsp]))

(def enabled-ident :wormbase.names/entity-type-enabled?)

(defmulti transform-ident-ref-value (fn [k m]
                                      k))

(defmethod transform-ident-ref-value :default [_ m]
  m)

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
  [identitfy-spec request identifier]
  (let [lookup-ref (s/conform identitfy-spec identifier)]
    (when (s/invalid? lookup-ref)
      (not-found! {:message "Malformed identifier"
                   :spec identitfy-spec
                   :data identifier}))
    (let [db (:db request)
          ent (d/entity db lookup-ref)]
      (if (:db/id ent)
        [lookup-ref ent]
        (not-found! {:message "Entity lookup failed"
                     :lookup-ref lookup-ref})))))

(defn prepare-data-for-transact
  "Strip any data keys that are not valid datomic idents."
  [db data]
  (select-keys data (filter (partial d/entid db) (keys data))))

(defn creator
  "Return an endpoint handler for new entity creation."
  ;; [uiident data-spec event summary-pull-expr & [validate-names]]
  [uiident conform-spec-fn event summary-pull-expr & [validate-names]]
  (fn handle-new [request]
    (let [{payload :body-params db :db conn :conn} request
          ent-ns (namespace uiident)
          live-status-attr (keyword ent-ns "status")
          live-status-val (keyword (str ent-ns ".status") "live")
          template (wbids/identifier-format db uiident)
          names-validator (if validate-names
                            (partial validate-names request)
                            identity)
          data (-> (:data payload)
                   (conform-spec-fn)
                   (wnu/qualify-keys ent-ns)
                   (names-validator)
                   (update live-status-attr (fnil identity live-status-val)))
          cdata (prepare-data-for-transact db (wnu/qualify-keys data ent-ns))
          prov (wnp/assoc-provenance request payload event)
          tx-data [['wormbase.ids.core/new template uiident [cdata]] prov]
          tx-res @(d/transact-async conn tx-data)
          dba (:db-after tx-res)]
      (when dba
        (let [new-id (wdb/extract-id tx-res uiident)
              emap (wdb/pull dba summary-pull-expr [uiident new-id])
              result {:created (wnu/unqualify-keys emap ent-ns)}]
          (created (str "/" ent-ns "/") result))))))

(defn merge-into-ent-data [data ent-data]
  (merge ent-data data))

(defn updater
  [identify-fn uiident conform-spec-fn event summary-pull-expr & [validate-names ref-resolver-fn]]
  (fn handle-update [request identifier]
    (let [{db :db conn :conn payload :body-params} request
          ent-ns (namespace uiident)
          [lur entity] (identify-fn request identifier)]
      (when entity
        (let [ent-data (wdb/pull db summary-pull-expr lur)
              names-validator (if validate-names
                                (partial validate-names request)
                                identity)
              data (-> payload
                       :data
                       (conform-spec-fn)
                       (wnu/qualify-keys ent-ns)
                       (merge-into-ent-data ent-data)
                       (names-validator)
                       (transform-ident-ref-values))
              resolve-refs-to-db-ids (or ref-resolver-fn
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
               (format "%s '%s' does not exist" ent-ns (last lur))))))))))

(defn status-changer
  "Return a handler to change the status of an entity to `to-status`.

  `identfiier` - A identifier string that must uniquely identify an entity.
  `to-status` - a keyword/ident identifiying an entity status. (e.g: :gene.status/live)
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

(defn generic-attrs [id-ident id-template]
  (let [entity-type (namespace id-ident)]
    [#:template{:describes id-ident :format id-template}
     #:db{:ident id-ident
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
     #:db{:ident (keyword (str entity-type ".status") "dead")}
     #:db{:ident (keyword (str entity-type ".status") "live")}
     #:db{:ident (keyword (str entity-type ".status") "suppressed")}]))

(defn new-entity-schema [conn entity-type id-template prov]
  (let [id-ident (keyword entity-type "id")
        tx-data (conj (generic-attrs id-ident id-template) prov)
        tx-res @(d/transact-async conn tx-data)
        enabled-tx-data [[:db/add id-ident enabled-ident true]]]
    (when (:db-after tx-res)
      @(d/transact-async conn enabled-tx-data)
      true)))

(defn handle-new-entity-schema [request]
  (let [{db :db conn :conn payload :body-params} request
        {data :data} payload
        {entity-type :entity-type id-template :id-template} data
        id-attr (keyword entity-type "id")
        prov (wnp/assoc-provenance request payload :event/new-entity-type)]
    (when-not entity-type
      (bad-request! {:message "entity-type not provided"}))
    (when (d/entity db id-attr)
      (conflict! {:message (format "Schema %s already exists! (:%s/id)"
                                   entity-type
                                   entity-type)}))
    (when (new-entity-schema conn entity-type id-template prov)
      (created (str "/api/" entity-type)
               {:message (format "Created generic schema for %s"
                                 entity-type)}))))

(defn list-entity-schemas [request]
  (let [entity-types (->> (d/q '[:find [(pull ?e pattern) ...]
                                 :in $ pattern
                                 :where
                                 [_ :template/describes ?e]
                                 [?e _ _]]
                               (:db request)
                               '[[:db/ident :as :entity-type]
                                 [:wormbase.names/entity-type-enabled? :as :enabled]])
                          (map #(update % :entity-type namespace))
                          (map #(update % :enabled (fnil identity true)))
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
    (when (test-fn curr-enabled?)
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
                                 {:not-found-message "Entity type is not installed."
                                  :conflict-message "Entity type is already disabled."})]
    (enabled-handler request ident enabled false :event/disable-entity-type)))

(defn enable-entity-type [request entity-type]
  (let [{:keys [ident enabled]} (check-enabled!
                                   request
                                   entity-type
                                   true?
                                   {:not-found-message "Entity type is already installed."
                                    :conflict-message "Entity type is already enabled."})]
    (enabled-handler request ident enabled true :event/enable-entity-type)))

(defn entity-enabled-checker
  "Middleware to check if an entity is enabled given information in the URL."
  [request-handler]
  (fn enabled-checker [request]
    (check-enabled! request
                    (-> request :params :entity-type)
                    false?
                    {:not-found-message "Entity type not installed."
                     :conflict-message "Entity type has been disabled and cannot be used."})
    (request-handler request)))

(def status-changed-responses
  (-> wnu/default-responses
      (assoc ok {:schema ::wse/status-changed})
      (wnu/response-map)))

(def coll-resources
  (sweet/context "" []
    :tags ["entity"]
    (sweet/resource
     {:get
      {:summary "List all simple entity types."
       :responses (wnu/response-map wnu/default-responses)
       :handler (fn handle-list-entity-schemas [request]
                  (list-entity-schemas request))}
      :post
      {:summary "Add a new simple entity to the system."
       :parameters {:body-params {:data ::wse/new-schema
                                  :prov ::wsp/provenance}}
       :handler (fn handle-new [request]
                  (handle-new-entity-schema request))}})))

(def item-resources
  (sweet/context "/:entity-type" []
    :tags ["entity"]
    :middleware [entity-enabled-checker]
    :path-params [entity-type :- ::wse/entity-type]
    (sweet/resource
     {:delete
      {:summary "Mark an entity type as disabled."
       :x-name ::disable-entity-type
       :responses (wnu/response-map wnu/default-responses)
       :parameters {:body-params {:prov ::wsp/provenance}}
       :handler (fn handle-disable-ent-type [request]
                  (disable-entity-type request entity-type))}
      :put
      {:summary "Update the schema for an entity type (enable/disable only)."
       :parameters {:body-params {:prov ::wsp/provenance}}
       :responses (wnu/response-map wnu/default-responses)
       :handler (fn handle-enable-entity-type [request]
                  (enable-entity-type request entity-type))}})
    (sweet/context "/:entity-type/:identifier" []
      :tags ["entity"]
      :path-params [identifier :- ::wse/identifier]
      (sweet/resource
       {:delete
        {:summary "Kill an entity."
         :x-name ::kill-entity
         :parameters {:body-params {:prov ::wsp/provenance}}
         :responses status-changed-responses
         :handler (fn handle-kill [request]
                    (let [status-ident (keyword (str entity-type ".status") "dead")
                          event-ident (keyword "event" (str "kill-" entity-type))
                          precond-failure-msg (format "%s to be killed is already dead." entity-type)
                          kill (status-changer status-ident
                                               event-ident
                                               :fail-precondition? wnu/dead?
                                               :precondition-failure-msg precond-failure-msg)]
                      (kill request identifier)))}
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
                          update-handler (updater identify
                                                  id-ident
                                                  (partial wnu/conform-data update-spec)
                                                  event-ident
                                                  summary-pull-expr)]
                      (update-handler request identifier)))}
        :get
        {:summary "Summarise an entity."
         :x-name ::about-entity
         :responses (-> wnu/default-responses
                        (assoc ok {:schema ::wse/summary})
                        (wnu/response-map))
         :handler (fn handle-entity-summary [request]
                    (let [summary-pull-expr (make-summary-pull-expr entity-type)]
                      ((summarizer identify summary-pull-expr #{})
                       request
                       identifier)))}})
      (sweet/context "/resurrect" []
        (sweet/resource
         {:post
          {:summary "Resurrect an entity."
           :x-name ::resurrect-entity
           :respones status-changed-responses
           :handler (fn handle-resurrect [request]
                      (let [status-ident (keyword (str entity-type ".status") "live")
                            event-ident (keyword "event" (str "resurrect-" entity-type))
                            precond-fail-msg (format "%s is already live." entity-type)
                            resurrect (status-changer status-ident
                                                      event-ident
                                                      :fail-precondition? wnu/live?
                                                      :precondition-failure-msg precond-fail-msg)]
                        (resurrect request identifier)))}})))))


(def routes (sweet/routes coll-resources
                          item-resources))
