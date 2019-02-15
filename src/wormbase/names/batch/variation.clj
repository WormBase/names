(ns wormbase.names.batch.variation
  (:require
   [clojure.spec.alpha :as s]
   [compojure.api.sweet :as sweet]
   [ring.util.http-response :refer [created ok]]
   [wormbase.specs.batch :as wsb]
   [wormbase.specs.provenance :as wsp]
   [wormbase.specs.variation :as wsv]
   [wormbase.names.auth :as wna]
   [wormbase.names.batch.generic :as wnbg]
   [wormbase.names.util :as wnu]))

(s/def ::prov ::wsp/provenance)

(def routes
  (sweet/context "/variation" []
    (sweet/resource
     {:put
      {:summary "Update variation records."
       :x-name ::batch-update-variations
       :middleware [wna/restrict-to-authenticated]
       :responses (wnu/response-map ok {:schema {:updated ::wsb/updated}})
       :parameters {:body-params {:data ::wsv/update-batch
                                  :prov ::prov}}
       :handler (fn handle-update [request]
                  (wnbg/update-entities :variation/id
                                        :event/update-variation
                                        ::wsv/update-batch
                                        wnu/conform-data
                                        request))}
      :post
      {:summary "Assign identifiers and associate names, creating new variations."
       :x-name ::batch-new-variations
       :middleware [wna/restrict-to-authenticated]
       :responses (wnu/response-map created {:schema ::wsb/created})
       :parameters {:body-params {:data ::wsv/new-batch
                                  :prov ::prov}}
       :handler (fn handle-new [request]
                  (let [event-type :event/new-variation
                        data (get-in request [:body-params])]
                    (wnbg/new-entities :variation/id
                                       event-type
                                       ::wsv/new-batch
                                       wnu/conform-data
                                       request)))}
      :delete
      {:summary "Kill variations."
       :x-name ::batch-kill-variations
       :middleware [wna/restrict-to-authenticated]
       :responses (wnu/response-map ok {:schema ::wsb/status-changed})
       :parameters {:body-params {:data ::wsv/kill-batch}}
       :handler (fn handle-kill [request]
                  (wnbg/change-entity-statuses :variation/id
                                               :event/kill-variation
                                               :variation.status/dead
                                               ::wsv/kill-batch
                                               wnbg/map-conform-data-drop-labels
                                               request))}})
    (sweet/POST "/resurrect" request
      :summary "Resurrect a batch of dead variations."
      :middleware [wna/restrict-to-authenticated]
      :body [data {:data ::wsv/resurrect-batch}
             prov {:prov :wsp/provenance}]
      (wnbg/change-entity-statuses :variation/id
                                   :event/resurrect-variation
                                   :variation.status/live
                                   ::wsv/resurrect-batch
                                   wnbg/map-conform-data-drop-labels
                                   request))
    (sweet/DELETE "/name" request
      :summary "Remove names from a batch of variations."
      :middleware [wna/restrict-to-authenticated]
      :body [data {:data ::wsv/names}
             prov {:prov ::wsp/provenance}]
      (wnbg/retract-attr-vals :variation/name
                              :variation/name
                              :event/remove-variation-name
                              ::wsv/names
                              wnu/conform-data
                              request))))
