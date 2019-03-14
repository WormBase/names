(ns wormbase.names.batch.sequence-feature
  (:require
   [compojure.api.sweet :as sweet]
   [wormbase.names.auth :as wna]
   [wormbase.names.batch.generic :as wnbg]
   [wormbase.names.util :as wnu]
   [wormbase.specs.provenance :as wsp]
   [wormbase.specs.sequence-feature :as wssf]))

(def summary-pull-expr '[* {:sequence-feature/status [:db/ident]}])

(defn conform
  "Conform the nubmer of desired features in data under `:n` to a collection.
  Required because wormbase.ids/new wants a collection."
  [spec data]
  (-> (wnu/conform-data spec data)
      :n
      (repeat {})))
 
(def routes
  (sweet/context "/sequence-feature" []
    :tags ["batch" "sequence-feature"]
    (sweet/resource
     {:post
      {:summary "Create new sequence-features."
       :x-name ::batch-new-sequence-feature
       :middleware [wna/restrict-to-authenticated]
       :parameters {:body-params {:data ::wssf/new-batch
                                  ;:prov ::wsp/provenance
                                  }}
       :handler (fn handle-new [request]
                  (wnbg/new-entities :sequence-feature/id
                                     :event/new-sequence-feature
                                     ::wssf/new-batch
                                     conform
                                     request))}
      :delete
      {:summary "Kill sequence features."
       :x-name ::batch-kill-sequence-feature
       :middleware [wna/restrict-to-authenticated]
       :parameters {:body-params {:data ::wssf/kill-batch
                                  :prov ::wsp/provenance}}
       :handler (fn handle-kill [request]
                  (wnbg/change-entity-statuses :sequence-feature/id
                                               :event/kill-sequence-feature
                                               :sequence-feature.status/dead
                                               ::wssf/kill-batch
                                               wnbg/map-conform-data-drop-labels
                                               request))}})
    (sweet/POST "/resurrect" request
      :summary "Resurrect a batch of dead sequence-features."
      :middleware [wna/restrict-to-authenticated]
      :body [data {:data ::wssf/resurrect-batch}
             prov {:prov :wsp/provenance}]
      (wnbg/change-entity-statuses :sequence-feature/id
                                   :event/resurrect-sequence-feature
                                   :sequence-feature.status/live
                                   ::wssf/resurrect-batch
                                   wnbg/map-conform-data-drop-labels
                                   request))))
(comment
  (conform-to-range ::wssf/new-batch 10))
