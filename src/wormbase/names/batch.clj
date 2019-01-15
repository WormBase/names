(ns wormbase.names.batch
  (:require
   [clojure.spec.alpha :as s]
   [datomic.api :as d]
   [compojure.api.sweet :as sweet]
   [ring.util.http-response :refer [bad-request conflict created ok]]
   [spec-tools.spec :as sts]
   [wormbase.names.auth :as wna]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.batch :as wsb]
   [wormbase.specs.gene :as wsg]
   [wormbase.specs.common :as wsc]
   [wormbase.specs.provenance :as wsp]
   [wormbase.ids.batch :as wbids-batch]
   [clojure.string :as str]
   [spec-tools.core :as stc]))

(s/def ::entity-type sts/string?)

(s/def ::prov ::wsp/provenance)

(defn find-entities [request]
  (bad-request "TBD"))

(defn- conform-spec-drop-label [spec data]
  (map second (wnu/conform-data spec data)))

(def ^:private default-batch-size 100)

(defn assign-status [entity-type to-status data]
  (let [status-ident (keyword entity-type "status")]
    (map #(assoc % status-ident to-status) data)))

(defn- batch-size [payload coll]
  (let [bsize (:batch-size payload)
        coll-size (count coll)
        cbsize (/ coll-size 10)]
    (if (nil? bsize)
      (if (> cbsize default-batch-size)
        default-batch-size
        cbsize)
      bsize)))

(defn batcher [impl uiident event-type spec request
               & {:keys [data-transform]
                  :or {data-transform conform-spec-drop-label}}]
  (let [{conn :conn payload :body-params} request
        {data :data} payload
        prov (wnp/assoc-provenance request payload event-type)
        cdata (data-transform spec data)
        bsize (batch-size payload cdata)]
    (impl conn uiident cdata prov :batch-size bsize)))

(defn new-entities
  "Create a batch of new entities."
  [uiident event-type request]
  (let [entity-type (namespace uiident)
        result (batcher wbids-batch/new
                        uiident
                        event-type
                        ::wsb/new
                        request
                        :data-transform (fn set-live [_ data]
                                          (let [live-status (keyword (str/join "." [entity-type "status"])
                                                                     "live")]
                                            (->> (conform-spec-drop-label ::wsb/new data)
                                                 (assign-status entity-type live-status)))))]
    (created (-> result :batch/id str) {:created result})))

(defn update-entities
  [uiident event-type request]
  (let [result (batcher wbids-batch/update
                        uiident
                        event-type
                        ::wsb/update
                        request)]
    (ok {:updated result})))

(defn change-entity-statuses [uiident event-type to-status spec request]
  (let [{conn :conn payload :body-params} request
        {data :data prov :prov} payload
        entity-type (namespace uiident)
        resp-key (name to-status)
        result (batcher wbids-batch/update
                        uiident
                        event-type
                        spec
                        request
                        :data-transform (fn txform-assign-status
                                          [_ data]
                                          (->> data
                                               (map (partial array-map uiident))
                                               (assign-status entity-type to-status))))]
    (ok {resp-key result})))

(defn retract-attr-vals
  "Retract values associated with attributes for a matching set of entities."
  [uiident attr event-type spec request]
  (let [{payload :body-params conn :conn} request
        {prov :prov data :data} payload
        cdata (some->> data
                       (stc/conform spec)
                       (filter (fn remove-any-nils [[_ value]]
                                 ((comp not nil?) value)))
                       (map (partial apply array-map)))]
    (if (s/invalid? cdata)
      (bad-request {:data data})
      (let [bsize (batch-size payload cdata)
            result (wbids-batch/retract
                    conn
                    uiident
                    attr
                    cdata
                    prov
                    :batch-size bsize)]
        (ok {:retracted result})))))

(def resources
  (sweet/context "/batch" []
    :tags ["batch"]
    (sweet/context "/gene" []
      (sweet/resource
       {:get
        {:summary "Find entities by any name."
         :x-name ::find-entities
         :responses (-> wnu/default-responses
                        (assoc ok {:schema ::wsc/find-result})
                        (wnu/response-map))
         :parameters {:query-params ::wsc/find-request}
         :handler (fn find-by-any-name [request]
                    ;; We know the entity type, can we use it to ompitise query?
                    (find-entities request))}
        :put
        {:summary "Update entity records."
         :x-name ::update-entities
         :middleware [wna/restrict-to-authenticated]
         :responses (-> wnu/default-responses
                        (assoc ok {:schema {:updated ::wsb/updated}})
                        (wnu/response-map))
         :parameters {:body-params {:data ::wsb/update
                                    :prov ::prov}}
         :handler (fn update-handler [request]
                    (update-entities :gene/id :event/update-gene request))}
        :post
        {:summary "Assign identifiers and associate names, creating new entities."
         :x-name ::new-entities
         :middleware [wna/restrict-to-authenticated]
         :responses (-> wnu/default-responses
                        (assoc created {:schema {:created ::wsb/created}})
                        (wnu/response-map))
         :parameters {:body-params {:data ::wsb/new
                                    :prov ::prov}}
         :handler (fn create-handler [request]
                    (let [event-type :event/new-gene
                          data (get-in request [:body-params])]
                      (new-entities :gene/id event-type request)))}
        :delete
        {:summary "Kill entities."
         :x-name ::kill-entities
         :responses (-> wnu/default-responses
                        (assoc ok {:schema ::wsb/status-changed}))
         :parameters {:body-params {:data ::wsg/kill-batch
                                    :prov ::prov
                                    :batch-size ::wsb/size}}
         :handler (partial change-entity-statuses
                           :gene/id
                           :event/kill-gene
                           :gene.status/dead
                           ::wsg/kill-batch)}})
      (sweet/POST "/resurrect" request
        :summary "Resurrect dead entities."
        :middleware [wna/restrict-to-authenticated]
        :body [{:keys [:data :prov]} {:data ::wsg/resurrect-batch
                                      :prov ::wsp/provenance
                                      :batch-size ::wsb/size}]
        (change-entity-statuses :gene/id
                                :event/resurrect-gene
                                :gene.status/live
                                ::wsg/resurrect-batch
                                request))
      (sweet/POST "/suppress" request
        :summary "Suppress entities."
        :middleware [wna/restrict-to-authenticated]
        :body [{:keys [:data :prov]} {:data ::wsg/suppress-batch
                                      :prov ::wsp/provenance
                                      :batch-size ::wsb/size}]
        (change-entity-statuses :gene/id
                                :event/suppress-gene
                                :gene.status/suppressed
                                ::wsg/suppress-batch
                                request))
      (sweet/DELETE "/cgc-name" request
        :summary "Remove CGC names from a gene."
        :middleware [wna/restrict-to-authenticated]
        :body [{:keys [:data :prov]} {:data ::wsg/cgc-names
                                      :prov ::wsp/provenance
                                      :batch-size ::wsb/size}]
        (retract-attr-vals :gene/cgc-name
                           :gene/cgc-name
                           :event/remove-cgc-names
                           ::wsg/cgc-names
                           request)))))

;; TODO:
;;;; /api/batch/<type>/?q= GET -> find-by-any-name-across-types
;;;; /api/batch/<type>/<batch-id>/ DELETE -> undo-batch-operation
;;;; /api/batch/<type>/<batch-id>/ GET -> batch-info
;;;; /api/batch/<type>/merge POST -> 1+ arrays of length 2+ g1, gN... (gN into g1)
;;;; /api/batch/<type>/split POST -> 1+ arrays of: gene-id, product-sequence-name, product-biotype

(def routes (sweet/routes resources))
