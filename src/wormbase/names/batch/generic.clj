(ns wormbase.names.batch.generic
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [compojure.api.sweet :as sweet]
   [clj-uuid :as uuid]
   [datomic.api :as d]
   [java-time :as jt]
   [ring.util.http-response :refer [bad-request created not-found! ok]]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]
   [wormbase.db :as wdb]
   [wormbase.ids.batch :as wbids-batch]
   [wormbase.names.entity :as wne]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.batch :as wsb]
   [wormbase.specs.entity :as wse]
   [wormbase.specs.provenance :as wsp]
   [java-time :as jt]))

(s/def ::entity-type sts/string?)

(s/def ::prov map?)

(defn map-conform-data-drop-labels [spec data]
  (map second (wnu/conform-data spec data)))

(def ^:private default-batch-size 100)

(defn assign-status [entity-type to-status data]
  (let [status-ident (keyword entity-type "status")]
    (map #(assoc % status-ident to-status) data)))

(defn batch-size [payload coll]
  (let [bsize (get payload :batch-size 0)
        coll-size (count coll)
        cbsize (int (/ coll-size 10))]
    (if (or (zero? cbsize) (> cbsize default-batch-size))
      default-batch-size
      cbsize)))

(defn batcher [impl uiident event-type spec data-transform request]
  (let [{conn :conn payload :body-params} request
        {data :data} payload
        prov (wnp/assoc-provenance request payload event-type)
        cdata (data-transform spec data)
        bsize (batch-size payload cdata)]
    (impl conn uiident cdata prov :batch-size bsize)))

(defn query-provenance [db bid pull-expr]
  (some->> (d/q '[:find [?tx ...]
                  :in $ ?bid
                  :where
                  [?tx :batch/id ?bid]]
                db
                bid)
           (map (partial wdb/pull db pull-expr))
           (map #(update %
                         :provenance/when
                         (fn [v] (jt/zoned-date-time v (jt/zone-id)))))
           (first)))

(defn ids-created [log db uiident batch-id name-attrs]
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
  "Create a batch of new entities."
  [uiident event-type spec conformer validator name-attrs request]
  (let [entity-type (namespace uiident)
        data-transform (fn set-live [_ data]
                         (let [live-status (keyword (str entity-type ".status") "live")]
                           (some->> data
                                    (conformer spec)
                                    (map #(wnu/qualify-keys % entity-type))
                                    (map wne/transform-ident-ref-values)
                                    (validator)
                                    (assign-status entity-type live-status))))
        batch-result (batcher wbids-batch/new
                              uiident
                              event-type
                              spec
                              data-transform
                              request)
        {conn :conn} request
        new-ids (ids-created (d/log conn)
                             (d/db conn)
                             uiident
                             (:id batch-result)
                             name-attrs)
        result (-> batch-result
                   (assoc :ids (map #(wnu/unqualify-keys % entity-type) new-ids)
                          :id-key uiident)
                   (wnu/unqualify-keys "batch"))]
    (created (str "/api/batch/" (:id result)) result)))

(defn update-entities
  [uiident item-pull-expr event-type spec conformer validator request]
  ;; TODO: conformer no longer applied. Is it needeed?
  (let [ent-ns (namespace uiident)
        data-transform (fn valdiating-conformer [_ data]
                         (let [{db :db} request
                               qdata (->> data
                                          (map #(wnu/qualify-keys % ent-ns))
                                          (map wne/transform-ident-ref-values)
                                          (validator))
                               db-data (map #(wdb/pull db item-pull-expr (find % uiident)) qdata)]
                           (map #(merge %1 %2) db-data qdata)))
        result (batcher wbids-batch/update
                        uiident
                        event-type
                        spec
                        data-transform
                        request)]
    (ok {:updated result})))

(defn convert-to-ids [db entity-type m]
  (let [k (-> m keys first)
        data (d/pull db [(keyword entity-type "id")] (find m k))]
    (when (nil? data)
      (throw (ex-info "Entity not found" {:data (wnu/unqualify-keys m entity-type)
                                          :type ::wdb/missing})))
    data))

(defn change-entity-statuses
  [uiident event-type to-status spec request]
  (let [{conn :conn db :db payload :body-params} request
        {data :data prov :prov} payload
        entity-type (namespace uiident)
        data-transform (fn txform-assign-status [_ data]
                         (->> data
                              (map #(wnu/qualify-keys % entity-type))
                              (map (partial convert-to-ids db entity-type))
                              (assign-status entity-type to-status)))
        resp-key (-> to-status name keyword)
        result (batcher wbids-batch/update
                        uiident
                        event-type
                        spec
                        data-transform
                        request)]
    (ok {resp-key result})))

(defn retract-attr-vals
  "Retract values associated with attributes for a matching set of entities."
  [uiident attr event-type spec conformer request]
  (let [{payload :body-params conn :conn} request
        ent-type (namespace uiident)
        data (:data payload)
        prov (wnp/assoc-provenance request payload :event/remove-cgc-names)
        conformed (conformer spec data)]
    (if (s/invalid? conformed)
      (bad-request {:data data})
      (let [cdata (some->> conformed
                           (filter (fn remove-any-nils [[_ value]]
                                     (not (nil? value))))
                           (map (partial apply assoc {}))
                           (map #(wnu/qualify-keys % ent-type)))
            bsize (batch-size payload cdata)
            result (wbids-batch/retract
                    conn
                    uiident
                    attr
                    cdata
                    prov
                    :batch-size bsize)]
        (ok {:retracted result})))))

(defn retract-names [request entity-type]
  (let [name-attr (keyword entity-type "name")
        event-ident (keyword "event" (str "remove-" entity-type "-name"))]
    (retract-attr-vals name-attr
                       name-attr
                       event-ident
                       ::wse/names
                       wnu/conform-data
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
       :responses (wnu/response-map ok {:schema {:updated ::wsb/updated}})
       :parameters {:body-params {:data ::wse/update-batch
                                  :prov ::wsp/provenance}}
       :handler (fn handle-update [request]
                  (let [ent-ident (keyword entity-type "id")
                        event-ident (keyword "event" (str "update-" entity-type))
                        pull-expr (wne/make-summary-pull-expr entity-type)]
                    (update-entities ent-ident
                                     pull-expr
                                     event-ident
                                     ::wse/update-batch
                                     wnu/conform-data
                                     identity
                                     request)))}
      :post
      {:summary "Assign identifiers and associate names, creating new variations."
       :x-name ::batch-new-entities
       :responses (wnu/response-map created {:schema ::wsb/created})
       :parameters {:body-params {:data ::wse/new-batch
                                  :prov ::wsp/provenance}}
       :handler (fn handle-new [request]
                  (let [ent-ident (keyword entity-type "id")
                        event-ident (keyword "event" (str "new-" entity-type))
                        data (get-in request [:body-params])
                        name-attrs [(keyword entity-type "name")]]
                    (new-entities ent-ident
                                  event-ident
                                  ::wse/new-batch
                                  wnu/conform-data
                                  identity
                                  name-attrs
                                  request)))}
      :delete
      {:summary "Kill a batch of entities."
       :x-name ::batch-kill-entities
       :responses (wnu/response-map ok {:schema ::wsb/status-changed})
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
      :body [data {:data ::wse/names}
             prov {:prov ::wsp/provenance}]
      (retract-names request entity-type))))
