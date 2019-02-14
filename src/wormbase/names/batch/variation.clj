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
       :x-name ::update-variations
       :middleware [wna/restrict-to-authenticated]
       :responses (-> wnu/default-responses
                      (assoc ok {:schema {:updated ::wsb/updated}})
                      (wnu/response-map))
       :parameters {:body-params {:data ::wsv/update-batch
                                  :prov ::prov}}
       :handler (fn handle-update [request]
                  (wnbg/update-entities :variation/name
                                        :event/update-variation
                                        ::wsv/update-batch
                                        wnu/conform-data
                                        request))}
      :post
      {:summary "Assign identifiers and associate names, creating new variations."
       :x-name ::new-variations
       :middleware [wna/restrict-to-authenticated]
       :responses (-> wnu/default-responses
                      (assoc created {:schema ::wsb/created})
                      (wnu/response-map))
       :parameters {:body-params {:data ::wsv/new-batch
                                  :prov ::prov}}
       :handler (fn handle-new [request]
                  (let [event-type :event/new-variation
                        data (get-in request [:body-params])]
                    (wnbg/new-entities :variation/name
                                       event-type
                                       ::wsv/new-batch
                                       wnu/conform-data
                                       request)))}
      :delete
      {:summary "Kill variations."
       :x-name ::kill-variations
       :middleware [wna/restrict-to-authenticated]
       :responses (-> wnu/default-responses
                      (assoc ok {:schema {:killed ::wsb/success-response}})
                      (wnu/response-map))
       :parameters {:body-params {:data ::wsv/kill-batch}}}})
    (sweet/POST "/resurrect" request
      :summary "Resurrect dead variations."
      :middleware [wna/restrict-to-authenticated]
      :body [data {:data ::wsv/resurrect-batch}
             prov {:prov :wsp/provenance}]
      (wnbg/change-entity-statuses :variation/id
                                   :event/resurrect-variation
                                   :variation.status/live
                                   ::wsv/resurrect-variation
                                   wnu/conform-data
                                   request))))
