(ns wormbase.names.batch
  (:require
   [clojure.spec.alpha :as s]
   [datomic.api :as d]
   [compojure.api.sweet :as sweet]
   [ring.util.http-response :refer [bad-request conflict created ok]]
   [spec-tools.spec :as sts]
   [wormbase.names.auth :as wna]
   [wormbase.names.util :as wnu]
   [wormbase.specs.batch :as wsb]
   [wormbase.specs.gene :as wsg]
   [wormbase.specs.common :as wsc]
   [wormbase.specs.provenance :as wsp]
   [wormbase.ids.batch :as wbids-batch]
   [clojure.string :as str]))

(s/def ::entity-type sts/string?)

(s/def ::prov ::wsp/provenance)

(defn find-entities [request]
  (bad-request "TBD"))

(defn- conform-spec-drop-label [request spec data]
  (map second (wnu/conform-data request spec data)))

(def ^:private default-batch-size 100)

(defn assign-status [uiident to-status data]
  (->> data
       (map (partial array-map uiident))
       (map #(assoc % :gene/status to-status))
       (set)))

(defn- batch-size [payload coll]
  (let [bsize (:batch-size payload)
        coll-size (count coll)
        cbsize (/ coll-size 10)]
    (if (nil? bsize)
      (if (> cbsize default-batch-size)
        default-batch-size
        cbsize)
      bsize)))

(defn batcher [impl entity-type spec request
               & {:keys [data-transform]
                  :or {data-transform conform-spec-drop-label}}]
  (let [{conn :conn payload :body-params} request
        {data :data prov :prov} payload
        cdata (data-transform request spec data)
        uiident (keyword entity-type "id")
        bsize (batch-size payload cdata)]
    (impl conn uiident cdata prov :batch-size bsize)))

(defn new-entities
  "Create a batch of new entities."
  [entity-type request]
  (let [result (batcher wbids-batch/new
                        entity-type
                        ::wsb/new request
                        :data-transform (fn set-live [_ _ data]
                                          (->> data
                                               (conform-spec-drop-label request ::wsb/new)
                                               (assign-status
                                                (keyword entity-type "id")
                                                (keyword (str/join "." [entity-type "status"]) "live")))))]
    (created (-> result :batch/id str) {:created result})))

(defn update-entities
  [entity-type request]
  (let [result (batcher wbids-batch/update entity-type ::wsb/update request)]
    (ok {:updated result})))

(defn change-entity-statuses [entity-type to-status spec request]
  (let [{conn :conn payload :body-params} request
        {data :data prov :prov} payload
        uiident (keyword entity-type "id")
        resp-key (name to-status)
        result (batcher wbids-batch/update
                        entity-type
                        spec
                        request
                        :data-transform (fn txform-assign-status
                                          [_ _ data]
                                          (assign-status uiident to-status data)))]
    (ok {resp-key result})))

(def resources
  (sweet/context "/batch/:entity-type" []
    :tags ["batch"]
    :path-params [entity-type :- ::entity-type]
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
      :handler (fn foo [request]
                 (update-entities entity-type request))}
     :post
     {:summary "Assign identifiers and associate names, creating new entities."
      :x-name ::new-entities
      :middleware [wna/restrict-to-authenticated]
      :responses (-> wnu/default-responses
                     (assoc created {:schema {:created ::wsb/created}})
                     (wnu/response-map))
      :parameters {:body-params {:data ::wsb/new
                                 :prov ::prov}}
      :handler (partial new-entities entity-type)}
     :delete
     {:summary "Kill entities."
      :x-name ::kill-entities
      :responses (-> wnu/default-responses
                     (assoc ok {:schema ::wsb/status-changed}))
      :parameters {:body-params {:data ::wsb/kill
                                 :prov ::prov
                                 :batch-size ::wsb/size}}
      :handler (partial change-entity-statuses
                        entity-type
                        :gene.status/dead
                        ::wsb/kill)}})
    (sweet/POST "/resurrect" request
      :summary "Resurrect dead entities."
      :middleware [wna/restrict-to-authenticated]
      :body [{:keys [:data :prov]} {:data ::wsb/resurrect
                                    :prov ::wsp/provenance
                                    :batch-size ::wsb/size}]
      (change-entity-statuses entity-type
                              :gene.status/live
                              ::wsb/resurrect
                              request))
    (sweet/POST "/suppress" request
      :summary "Suppress entities."
      :middleware [wna/restrict-to-authenticated]
      :body [{:keys [:data :prov]} {:data ::wsb/suppress
                                    :prov ::wsp/provenance
                                    :batch-size ::wsb/size}]
      (change-entity-statuses entity-type
                              :gene.status/suppressed
                              ::wsb/suppressed
                              request))))
;; TODO:
;;;; /api/batch/<type>/?q= GET -> find-by-any-name-across-types
;;;; /api/batch/<type>/<batch-id>/ DELETE -> undo-batch-operation
;;;; /api/batch/<type>/<batch-id>/ GET -> batch-info
;;;; /api/batch/<type>/merge POST -> 1+ arrays of length 2+ g1, gN... (gN into g1)
;;;; /api/batch/<type>/split POST -> 1+ arrays of: gene-id, product-sequence-name, product-biotype

(def routes (sweet/routes resources))
