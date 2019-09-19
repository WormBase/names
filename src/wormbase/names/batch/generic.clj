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
   [wormbase.specs.batch :as wsb]
   [wormbase.specs.provenance :as wsp]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.variation :as wsv]
   [java-time :as jt]))

(s/def ::entity-type sts/string?)

(s/def ::prov ::wsp/provenance)

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
       (filter #((-> name-attrs (conj uiident) set) (:attr %)))
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

(defn change-entity-statuses
  [uiident event-type to-status spec conformer request]
  (let [{conn :conn payload :body-params} request
        {data :data prov :prov} payload
        entity-type (namespace uiident)
        data-transform (fn txform-assign-status [_ data]
                         (->> (conformer spec data)
                              (map (partial assoc {} uiident))
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
        data (:data payload)
        prov (wnp/assoc-provenance request payload :event/remove-cgc-names)
        conformed (conformer spec data)]
    (if (s/invalid? conformed)
      (bad-request {:data data})
      (let [cdata (some->> conformed
                           (filter (fn remove-any-nils [[_ value]]
                                     (not (nil? value))))
                           (map (partial apply assoc {})))
            bsize (batch-size payload cdata)
            result (wbids-batch/retract
                    conn
                    uiident
                    attr
                    cdata
                    prov
                    :batch-size bsize)]
        (ok {:retracted result})))))

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
        (wnu/unqualify-keys "batch")
        (wnu/unqualify-keys "provenance")
        (update :who (fn [who]
                       (wnu/unqualify-keys who "person")))
        (ok))))

;;; TODO:
;; (def routes
;;   (sweet/context "/:entity-type" []
;;     :tags ["batch" "variation"]
;;     :path-params [entity-type string?]
;;     (sweet/resource
;;      {:put
;;       {:summary "Update variation records."
;;        :x-name ::batch-update-variations
;;        :responses (wnu/response-map ok {:schema {:updated ::wsb/updated}})
;;        :parameters {:body-params {:data ::wsv/update-batch
;;                                   :prov ::wsp/provenance}}
;;        :handler (fn handle-update [request]
;;                   (update-entities :variation/id
;;                                    wnv/summary-pull-expr
;;                                    :event/update-variation
;;                                    ::wsv/update-batch
;;                                    wnu/conform-data
;;                                    identity
;;                                    request))}
;;       :post
;;       {:summary "Assign identifiers and associate names, creating new variations."
;;        :x-name ::batch-new-variations
;;        :responses (wnu/response-map created {:schema ::wsb/created})
;;        :parameters {:body-params {:data ::wsv/new-batch
;;                                   :prov ::wsp/provenance}}
;;        :handler (fn handle-new [request]
;;                   (let [event-type :event/new-variation
;;                         data (get-in request [:body-params])]
;;                     (new-entities :variation/id
;;                                   event-type
;;                                   ::wsv/new-batch
;;                                   wnu/conform-data
;;                                   identity
;;                                   [:variation/name]
;;                                   request)))}
;;       :delete
;;       {:summary "Kill variations."
;;        :x-name ::batch-kill-variations
;;        :responses (wnu/response-map ok {:schema ::wsb/status-changed})
;;        :parameters {:body-params {:data ::wsv/kill-batch}}
;;        :handler (fn handle-kill [request]
;;                   (change-entity-statuses :variation/id
;;                                           :event/kill-variation
;;                                           :variation.status/dead
;;                                           ::wsv/kill-batch
;;                                           map-conform-data-drop-labels
;;                                           request))}})
;;     (sweet/POST "/resurrect" request
;;       :summary "Resurrect a batch of dead variations."
;;       :body [data {:data ::wsv/resurrect-batch}
;;              prov {:prov :wsp/provenance}]
;;       (change-entity-statuses :variation/id
;;                               :event/resurrect-variation
;;                               :variation.status/live
;;                               ::wsv/resurrect-batch
;;                               map-conform-data-drop-labels
;;                               request))
;;     (sweet/DELETE "/name" request
;;       :summary "Remove names from a batch of variations."
;;       :body [data {:data ::wsv/names}
;;              prov {:prov ::wsp/provenance}]
;;       (retract-attr-vals :variation/name
;;                          :variation/name
;;                          :event/remove-variation-name
;;                          ::wsv/names
;;                          wnu/conform-data
;;                          request))))
