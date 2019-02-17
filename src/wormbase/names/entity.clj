(ns wormbase.names.entity
  (:require
   [clojure.spec.alpha :as s]
   [datomic.api :as d]
   [ring.util.http-response :refer [bad-request! created not-found not-found! ok]]
   [wormbase.db :as wdb]
   [wormbase.util :as wu]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.ids.core :as wbids]))

(defn identify
  "Return an lookup ref and entity for a given identifier.
  Lookups `identifier` (conformed with `identify-spec`) in the database.
  Returns `nil` when the entity cannot be found."
  [identitfy-spec request identifier]
  (when-not (s/valid? identitfy-spec identifier)
    (throw (ex-info "Found one or more invalid identifiers."
                    {:problems (s/explain-data identitfy-spec identifier)
                     :type ::validation-error})))
  (let [lookup-ref (s/conform identitfy-spec identifier)
        db (:db request)
        ent (d/entity db lookup-ref)]
    (if (:db/id ent)
      [lookup-ref ent]
      (not-found! {:message "Entity lookup failed"
                   :lookup-ref lookup-ref}))))

(defn creator
 "Return an endpoint handler for new entity creation."
  ;; [uiident data-spec event info-pull-expr & [validate-names]]
  [uiident conform-spec-fn event info-pull-expr & [validate-names]]
  (fn handle-new [request]
    (let [{payload :body-params db :db conn :conn} request
          ent-ns (namespace uiident)
          live-status-attr (keyword ent-ns "status")
          live-status-val (keyword (str ent-ns ".status") "live")
          template (wbids/identifier-format db uiident)
          data (-> payload
                   :data
                   (update live-status-attr (fnil identity live-status-val)))
          names-validator (or validate-names (constantly (identity data)))
          cdata (conform-spec-fn data (partial names-validator request))
          prov (wnp/assoc-provenance request payload event)
          tx-data [['wormbase.ids.core/new template uiident [cdata]] prov]
          tx-res @(d/transact-async conn tx-data)
          dba (:db-after tx-res)
          new-id (wdb/extract-id tx-res uiident)
          emap (wdb/pull dba info-pull-expr [uiident new-id])
          result {:created emap}]
      (created (str "/" ent-ns "/") result))))

(defn updater
  [identify-fn uiident conform-spec-fn event info-pull-expr & [validate-names ref-resolver-fn]]
  (fn handle-update [request identifier]
    (let [{db :db conn :conn payload :body-params} request
          ent-ns (namespace uiident)
          [lur entity] (identify-fn request identifier)]
      (when entity
        (let [data (:data payload)
              names-validator (if validate-names
                                (partial validate-names request))]
          (let [resolve-refs-to-db-ids (or ref-resolver-fn
                                           (fn passthru-resolver [_ data]
                                             data))
                cdata (->> (conform-spec-fn data names-validator)
                           (resolve-refs-to-db-ids db))
                prov (wnp/assoc-provenance request payload event)
                txes [['wormbase.ids.core/cas-batch lur cdata] prov]
                tx-result @(d/transact-async conn txes)]
            (when-let [db-after (:db-after tx-result)]
              (if-let [updated (wdb/pull db-after info-pull-expr lur)]
                (ok {:updated updated})
                (not-found
                 (format "%s '%s' does not exist" ent-ns (last lur)))))))))))

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
  [identifier-spec status-ident to-status event-type
   & {:keys [fail-precondition? precondition-failure-msg]
      :or {precondition-failure-msg "status cannot be updated."}}]
  (fn change-status
    [request identifier]
    (let [{conn :conn db :db payload :body-params} request
          lur (s/conform identifier-spec identifier)
          pull-status #(d/pull % [{status-ident [:db/ident]}] lur)
          {status status-ident} (pull-status db)]
      (when (and status
                 fail-precondition?
                 (fail-precondition? status))
        (bad-request! {:message precondition-failure-msg
                       :info (wu/undatomicize db status)}))
      (let [prov (wnp/assoc-provenance request payload event-type)
            tx-res @(d/transact-async
                     conn [[:db/cas
                            lur
                            status-ident
                            (d/entid db (:db/ident status))
                            (d/entid db to-status)]
                           prov])
            dba (:db-after tx-res)]
        (->> dba
             pull-status
             (wu/undatomicize dba)
             ok)))))

(defn summarizer [identify-fn pull-expr]
  (fn handle-summary [request identifier]
    (let [db (:db request)
          [lur _] (identify-fn request identifier)]
      (when-let [info (wdb/pull db pull-expr lur)]
        (let [prov (wnp/query-provenance db lur)]
          (-> info (assoc :history prov) ok))))))
