(ns wormbase.names.batch.generic
  (:require
   [clojure.spec.alpha :as s]
   [compojure.api.sweet :as sweet]
   [clj-uuid :as uuid]
   [datomic.api :as d]
   [java-time :as jt]
   [ring.util.http-response :refer [bad-request created not-found! not-modified ok]]
   [spec-tools.spec :as sts]
   [wormbase.db :as wdb]
   [wormbase.ids.batch :as wbids-batch]
   [wormbase.names.entity :as wne]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.names.validation :as wnv]
   [wormbase.specs.batch :as wsb]
   [wormbase.specs.entity :as wse]
   [wormbase.specs.provenance :as wsp]))

(s/def ::entity-type sts/string?)

(s/def ::prov map?)

(def ^:private default-batch-size 100)

(def status-changed-responses
  (-> wnu/default-responses
      (dissoc not-modified)
      (assoc ok {:schema ::wsb/status-changed
                 :descrpition "Information provided about entity status changes."})
      (wnu/response-map)))

(defn conform-data-drop-labels
  "Conform data to an 'or' spec, striping away the label.

  An 'or' spec is a spec defined with `clojure.spec.alpha/or`."
  [spec data]
  (second (wnu/conform-data spec data)))

(defn assign-status
  "Assign a status for an entity type in `data`.

  Returns a map."
  [entity-type to-status data]
  (let [status-ident (keyword entity-type "status")]
    (assoc data status-ident to-status)))

(defn batch-size
  "Calculate a batch size given the input data collection `coll`.

  If the collection is smaller that the default batch size, the the default (100) will be used,
  otherwise the collection size divided by the default batch size."
  [coll]
  (let [coll-size (count coll)]
    (if (< coll-size default-batch-size)
      default-batch-size
      (int (/ coll-size default-batch-size)))))

(defn batcher
  "Perform batching given an implementation function `impl`, applying `data-transform` to
  the data in the `:body-params` of the request."
  [impl uiident event-type spec data-transform request]
  (let [{conn :conn payload :body-params} request
        {data :data} payload
        prov (wnp/assoc-provenance request payload event-type)
        cdata (data-transform spec data)
        bsize (batch-size cdata)]
    (impl conn uiident cdata prov :batch-size bsize)))

(defn query-provenance
  "Query the database for the provenance associated with a given batch identifier `bid`,
  and return a map of the provenance data associated as described by `pull-expr`."
  [db bid pull-expr]
  (some->> (d/q '[:find [?tx ...]
                  :in $ ?bid
                  :where
                  [?tx :batch/id ?bid]]
                db
                bid)
           (map (partial wdb/pull db pull-expr))
           (map #(update %
                         :provenance/when
                         (fn [v]
                           (jt/zoned-date-time v (jt/zone-id)))))
           (first)))

(defn ids-created
  "Given a uniquely identifying ident (`uiident`) and a `batch-id` value,
  find the identifiers created by that batch.

  Return a sequence of maps with the attribute/value pairs (identifiers)."
  [log db uiident batch-id name-attrs]
  (->> (d/q '[:find ?tx .
              :in $ ?bid
              :where
              [?tx :batch/id ?bid]]
            db
            batch-id)
       (wnp/tx-changes db log)
       (filter #(not= (:value %) batch-id))
       (filter #((set (conj name-attrs (name uiident))) (name (:attr %))))
       (group-by :eid)
       (vals)
       (map (fn [xs]
              (->> xs
                   (map (fn [item]
                          {(:attr item) (:value item)}))
                   (apply merge))))))

(defn new-entities
  "Create a batch of new entities.

  Accepts a batch of new entities via a HTTP request as described by a given `spec`.
  This func should return the new identifiers created.
  It may raise an exception if transacting the batch would cause a conflict, or if
  any referenced entities are missing in the database.

  - uiident : Uniquely Identifing Ident - A datomic entity attribute.
  - event-type: A datomic ident that identifies the kind of event.
  - spec: The spec desribing the shape of the data.
  - conformer: function taking spec and data, responsible for coercion of input data.
  - validator: function to validate the coerced data.
  - name-attrs: A set of attributes desciring the names for the entities in input data.
  - request: The HTTP request.
  "
  [uiident event-type spec conformer validator name-attrs request]
  (let [{conn :conn db :db} request
        entity-type (namespace uiident)
        ident-ent (d/entity db uiident)
        named? (:wormbase.names/name-required? ident-ent)
        data-transform (fn [_ data]
                         (let [live-status (keyword (str entity-type ".status") "live")
                               unnamed-status-repeater (fn [x n]
                                                         (repeat n
                                                                 (assign-status entity-type live-status x)))
                               conformed (conformer spec data)
                               transformed (if named?
                                             (some->> conformed
                                                      (map #(wnu/qualify-keys % entity-type))
                                                      (map wne/transform-ident-ref-values)
                                                      (map (partial assign-status entity-type live-status)))
                                             (-> (dissoc conformed :n)
                                                 (wnu/qualify-keys entity-type)
                                                 (wne/transform-ident-ref-values)
                                                 (unnamed-status-repeater (:n conformed))))]
                           (when-let [errors (some->> (map validator transformed)
                                                      (filter identity)
                                                      (seq))]
                             (throw (ex-info "One ore more invalid names found."
                                             {:type :user/validation-error
                                              :errors errors})))
                           transformed))
        batch-result (batcher wbids-batch/new
                              uiident
                              event-type
                              spec
                              data-transform
                              request)
        new-ids (ids-created (d/log conn)
                             (d/db conn)
                             uiident
                             (:id batch-result)
                             name-attrs)
        result (-> batch-result
                   (assoc :ids (map #(wnu/unqualify-keys % entity-type) new-ids))
                   (wnu/unqualify-keys "batch"))]
    (created (str "/api/batch/" (:id result)) result)))

(defn update-entities
  "Perform a batch update.

  - uiident : Uniquely Identifing Ident - A datomic entity attribute.
  - get-info-fn: a fn to pull the datomic data for each entity
                 that's fetched as defaults when the data isn't supplied in the request.
  - event-type: A datomic ident that identifies the kind of event.
  - spec: The spec desribing the shape of the data.
  - conformer: function taking spec and data, responsible for coercion of input data.
  - validator: function to validate the coerced data.
  - request: the HTTP request."
  [uiident get-info-fn event-type spec _ validator request]
  (let [ent-ns (namespace uiident)
        data-transform (fn valdiating-conformer [_ data]
                         (let [{db :db} request
                               qdata (map #(wnu/qualify-keys % ent-ns) data)
                               db-data (map #(get-info-fn db (find % uiident)) qdata)
                               transformed (->> qdata
                                                (map merge db-data)
                                                (map wne/transform-ident-ref-values))]
                           (when-let [errors (some->> (map validator transformed)
                                                      (filter identity)
                                                      (seq))]
                             (throw (ex-info "One or more invalid names found."
                                            {:errors errors})))
                           transformed))]
    (ok {:updated (batcher wbids-batch/update
                           uiident
                           event-type
                           spec
                           data-transform
                           request)})))

(defn convert-to-ids [db entity-type m]
  (let [k (-> m keys first)
        data (d/pull db [(keyword entity-type "id")] (find m k))]
    (when (nil? data)
      (throw (ex-info "Entity not found" {:data (wnu/unqualify-keys m entity-type)
                                          :type ::wdb/missing})))
    data))

(defn change-entity-statuses
  [uiident event-type to-status spec request]
  (let [{db :db} request
        entity-type (namespace uiident)
        data-transform (fn txform-assign-status [_ data]
                         (->> data
                              (map #(wnu/qualify-keys % entity-type))
                              (map (partial convert-to-ids db entity-type))
                              (map (partial assign-status entity-type to-status))))
        resp-key (-> to-status name keyword)
        result (batcher wbids-batch/update
                        uiident
                        event-type
                        spec
                        data-transform
                        request)]
    (ok {resp-key result})))

(defn adjust-attr-vals
  "Adjust (add/retract) values associated with attributes for a matching set of entities.

   - uiident : The datomic `ident` that uniquely identifies
               an entity for each mapping in the request.
   - attr: the datomic attribute name to add/retract value(s) to/from
           for each mapping in the request.
   - event: A datomic ident that identifies the kind of event.
   - spec: The spec desribing the shape of the data.
   - conformer: function taking spec and data, responsible for coercion of input data.
   - add: boolean indicating whether to add (true) or retract (false) values to/from `attr`.
   - request: the HTTP request."
  [uiident attr event spec conformer ^Boolean add request]
  (let [{payload :body-params conn :conn} request
        ent-type (namespace uiident)
        data (:data payload)
        prov (wnp/assoc-provenance request payload event)
        conformed (conformer spec data)]
    (if (s/invalid? conformed)
      (bad-request {:data data})
      (let [cdata (some->> conformed
                           (map #(wnu/qualify-keys % ent-type))
                           (filter #(some? (attr %))))
            bsize (batch-size cdata)
            result (wbids-batch/adjust-attr
                    conn
                    add
                    uiident
                    attr
                    cdata
                    prov
                    :batch-size bsize)]
        (ok result)))))

(defn retract-names [request entity-type]
  (let [name-attr (keyword entity-type "name")
        event-ident (keyword "event" (str "remove-" entity-type "-name"))]
    (adjust-attr-vals name-attr
                      name-attr
                      event-ident
                      ::wse/names
                      wnu/conform-data
                      false
                      request)))

(defn summary [request bid pull-expr]
  (let [{db :db} request
        batch-id (uuid/as-uuid bid)
        b-prov-summary (query-provenance db batch-id pull-expr)]
    (when-not b-prov-summary
      (not-found!))
    (-> b-prov-summary
        (assoc :id batch-id)
        (update :provenance/when (fn [v]
                                   (when v
                                     (jt/zoned-date-time v (jt/zone-id)))))
        (update :provenance/who (fn [who]
                                  (wnu/unqualify-keys who "person")))
        (wnu/unqualify-keys "batch")
        (wnu/unqualify-keys "provenance")
        (update :how wnu/unqualify-maybe)
        (update :what wnu/unqualify-maybe)
        (ok))))

(def routes
  (sweet/context "/entity/:entity-type" []
    :tags ["batch"]
    :path-params [entity-type :- string?]
    (sweet/resource
     {:put
      {:summary "Update records."
       :x-name ::batch-update-entities
       :responses (-> wnu/default-responses
                      (assoc ok {:schema {:updated ::wsb/updated}})
                      (wnu/response-map))
       :parameters {:body-params {:data ::wse/update-batch
                                  :prov ::wsp/provenance}}
       :handler (fn handle-update [request]
                  (let [ent-ident (keyword entity-type "id")
                        event-ident (keyword "event" (str "update-" entity-type))
                        summary-fn (partial wne/pull-ent-summary entity-type)]
                    (update-entities ent-ident
                                     summary-fn
                                     event-ident
                                     ::wse/update-batch
                                     wnu/conform-data
                                     (partial wnv/validate-names request)
                                     request)))}
      :post
      {:summary "Assign identifiers and associate names, creating new entities."
       :x-name ::batch-new-entities
       :responses (-> wnu/default-responses
                      (assoc created {:schema ::wsb/created})
                      (wnu/response-map))
       :parameters {:body-params {:data ::wse/new-batch
                                  :prov ::wsp/provenance}}
       :handler (fn handle-new [request]
                  (let [ent-ident (keyword entity-type "id")
                        ident-ent (d/entity (:db request) ent-ident)
                        event-ident (keyword "event" (str "new-" entity-type))
                        data (get-in request [:body-params])
                        name-attrs (when (:wormbase.names/name-required? ident-ent)
                                     [(keyword entity-type "name")])]
                    (new-entities ent-ident
                                  event-ident
                                  ::wse/new-batch
                                  conform-data-drop-labels
                                  (partial wnv/validate-names request)
                                  name-attrs
                                  request)))}
      :delete
      {:summary "Kill a batch of entities."
       :x-name ::batch-kill-entities
       :responses status-changed-responses
       :parameters {:body-params {:data ::wse/kill-batch}}
       :handler (fn handle-kill [request]
                  (let [ent-ident (keyword entity-type "id")
                        event-ident (keyword "event" (str "kill-" entity-type))
                        dead-status (keyword (str entity-type ".status") "dead")]
                    (change-entity-statuses ent-ident
                                            event-ident
                                            dead-status
                                            ::wse/kill-batch
                                            request)))}})
    (sweet/POST "/resurrect" request
      :summary "Resurrect a batch of dead entities."
      :responses status-changed-responses
      :body [data {:data ::wse/resurrect-batch}
             prov {:prov ::wsp/provenance}]
      (let [ent-ident (keyword entity-type "id")
            event-ident (keyword "event" (str "resurrect-" entity-type))
            status (keyword (str entity-type ".status") "live")]
        (change-entity-statuses ent-ident
                                event-ident
                                status
                                ::wse/resurrect-batch
                                request)))
    (sweet/DELETE "/name" request
      :summary "Remove names from a batch of entities."
      :responses status-changed-responses
      :body [data {:data ::wse/names}
             prov {:prov ::wsp/provenance}]
      (retract-names request entity-type))))
