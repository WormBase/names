(ns wormbase.names.batch.generic
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clj-uuid :as uuid]
   [datomic.api :as d]
   [java-time :as jt]
   [ring.util.http-response :refer [bad-request created ok]]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]
   [wormbase.db :as wdb]
   [wormbase.ids.batch :as wbids-batch]
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
  (let [bsize (:batch-size payload)
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

(defn ids-created [db uiident batch-id]
  (sort (d/q '[:find [?identifier ...]
               :in $ ?bid ?ident
               :where
               [?tx :batch/id ?bid]
               [?e ?ident ?identifier ?tx]]
             db
             batch-id
             uiident)))

(defn new-entities
  "Create a batch of new entities."
  [uiident event-type spec conformer validator request]
  (let [entity-type (namespace uiident)
        data-transform (fn set-live [_ data]
                         (let [live-status (keyword (str entity-type ".status") "live")]
                           (->> (validator data)
                                (conformer spec)
                                (assign-status entity-type live-status))))
        batch-result (batcher wbids-batch/new
                              uiident
                              event-type
                              spec
                              data-transform
                              request)
        new-ids (ids-created (-> request :conn d/db) uiident (:batch/id batch-result))
        result (assoc batch-result :ids new-ids :id-key uiident)]
    (created (str "/api/batch/" (:batch/id result)) result)))

(defn update-entities
  [uiident event-type spec conformer validator request]
  (let [data-transform (fn valdiating-conformer [_ data]
                         (conformer spec (validator data)))
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
                              (map (partial array-map uiident))
                              (assign-status entity-type to-status)))
        entity-type (namespace uiident)
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
        {prov :prov data :data} payload
        conformed (conformer spec data)]
    (if (s/invalid? conformed)
      (bad-request {:data data})
      (let [cdata (some->> conformed
                           (filter (fn remove-any-nils [[_ value]]
                                     ((comp not nil?) value)))
                           (map (partial apply array-map)))
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
    (when b-prov-summary
      (-> b-prov-summary
          (assoc :batch/id batch-id)
          (update :provenance/when (fn [v]
                                     (when v
                                       (jt/zoned-date-time v (jt/zone-id)))))
          (ok)))))
