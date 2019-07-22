(ns wormbase.names.batch.gene
  (:require
   [clojure.spec.alpha :as s]
   [compojure.api.sweet :as sweet]
   [ring.util.http-response :refer [bad-request! created ok]]
   [spec-tools.core :as stc]
   [wormbase.ids.batch :as wbids-batch]
   [wormbase.names.auth :as wna]
   [wormbase.names.batch.generic :as wnbg]
   [wormbase.names.gene :as wng]
   [wormbase.names.util :as wnu]
   [wormbase.specs.batch :as wsb]
   [wormbase.specs.gene :as wsg]
   [wormbase.specs.provenance :as wsp]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.validation :as wnv]
   [expound.alpha :as expound]))

(s/def ::prov ::wsp/provenance)

(defn merge-genes [event-type spec request]
  (let [{conn :conn payload :body-params} request
        {data :data prov :prov} payload
        cdata (stc/conform spec data)]
    (when (s/invalid? cdata)
      (bad-request! {:data data
                     :problems (expound/expound-str spec data)}))
    (let [bsize (wnbg/batch-size payload data)]
      (ok (wbids-batch/merge-genes conn cdata prov :batch-size bsize)))))

(defn split-genes [event-type spec request]
  (let [{conn :conn payload :body-params} request
        {data :data prov :prov} payload
        cdata (stc/conform spec data)]
    (when (s/invalid? cdata)
      (bad-request! {:data data
                     :problems (expound/expound-str spec data)}))
    (let [bsize (wnbg/batch-size payload data)]
      (ok (wbids-batch/split-genes conn cdata prov :batch-size bsize)))))

(defn names-validator [request data]
  (map (partial wnv/validate-names request) data))

(def routes
  (sweet/context "/gene" []
    :tags ["batch" "gene"]
    (sweet/resource
     {:put
      {:summary "Update gene records."
       :x-name ::batch-update-genes
       :responses (wnu/response-map ok {:schema {:updated ::wsb/updated}})
       :parameters {:body-params {:data ::wsg/update-batch
                                  :prov ::prov}}
       :handler (fn update-handler [request]
                  (wnbg/update-entities :gene/id
                                        wng/summary-pull-expr
                                        :event/update-gene
                                        ::wsg/update-batch
                                        wnu/conform-data
                                        (partial names-validator request)
                                        request))}
      :post
      {:summary "Assign identifiers and associate names, creating new genes."
       :x-name ::batch-new-genes
       :responses (wnu/response-map created {:schema ::wsb/created})
       :parameters {:body-params {:data ::wsg/new-batch
                                  :prov ::prov}}
       :handler (fn create-handler [request]
                  (let [event-type :event/new-gene
                        data (get-in request [:body-params])]
                    (wnbg/new-entities :gene/id
                                       event-type
                                       ::wsg/new-batch
                                       wnbg/map-conform-data-drop-labels
                                       (partial names-validator request)
                                       [:gene/cgc-name :gene/sequence-name]
                                       request)))}
      :delete
      {:summary "Kill genes."
       :x-name ::batch-kill-genes
       :responses (wnu/response-map ok {:schema ::wsb/status-changed})
       :parameters {:body-params {:data ::wsg/kill-batch
                                  :prov ::prov}}
       :handler (partial wnbg/change-entity-statuses
                         :gene/id
                         :event/kill-gene
                         :gene.status/dead
                         ::wsg/kill-batch
                         wnbg/map-conform-data-drop-labels)}})
    (sweet/POST "/resurrect" request
      :summary "Resurrect dead genes."
      :body [data {:data ::wsg/resurrect-batch}
             prov {:prov :wsp/provenance}]
      (wnbg/change-entity-statuses :gene/id
                                   :event/resurrect-gene
                                   :gene.status/live
                                   ::wsg/resurrect-batch
                                   wnbg/map-conform-data-drop-labels
                                   request))
    (sweet/POST "/suppress" request
      :summary "Suppress genes."
      :body [data {:data ::wsg/suppress-batch}
             prov {:prov ::wsp/provenance}]
      (wnbg/change-entity-statuses :gene/id
                                   :event/suppress-gene
                                   :gene.status/suppressed
                                   ::wsg/suppress-batch
                                   wnbg/map-conform-data-drop-labels
                                   request))
    (sweet/DELETE "/cgc-name" request
      :summary "Remove CGC names from a gene."
      :body [data {:data ::wsg/cgc-names}
             prov {:prov ::wsp/provenance}]
      (wnbg/retract-attr-vals :gene/cgc-name
                              :gene/cgc-name
                              :event/remove-cgc-names
                              ::wsg/cgc-names
                              wnu/conform-data
                              request))
    (sweet/context "/merge" []
      :tags ["batch" "gene"]
      (sweet/resource
       {:post
        {:summary "Merge multiple pairs of genes."
         :responses (wnu/response-map ok {:schema ::wsb/success-response})
         :parameters {:body-params {:data ::wsg/merge-gene-batch
                                    :prov ::wsp/provenance}}
         :handler (fn [request]
                    (merge-genes :event/merge-genes ::wsg/merge-gene-batch request))}}))
    (sweet/context "/split" []
      :tags ["batch" "gene"]
      (sweet/resource
       {:post
        {:summary "Split multiple genes."
         :responses (wnu/response-map ok {:schema ::wsb/success-response})
         :parameters {:body-params {:data ::wsg/split-gene-batch
                                    :prov ::wsp/provenance}}
         :handler (fn [request]
                    (split-genes :event/split-genes ::wsg/split-gene-batch request))}}))))
