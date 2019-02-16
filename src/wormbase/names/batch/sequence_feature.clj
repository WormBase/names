(ns wormbase.names.batch.sequence-feature
  (:require
   [compojure.api.sweet :as sweet]
   [wormbase.specs.sequence-feature :as wssf]
   [wormbase.names.auth :as wna]
   [wormbase.specs.provenance :as wsp]
   [wormbase.names.batch.generic :as wnbg]
   [wormbase.names.util :as wnu]))

(def info-pull-expr '[* {:sequence-feature/status [:db/ident]}])

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
       :x-name ::batch-new-features
       :middleware [wna/restrict-to-authenticated]
       :parameters {:body-params {:data ::wssf/new-batch
                                  ;:prov ::wsp/provenance
                                  }}
       :handler (fn handle-new [request]
                  (wnbg/new-entities :sequence-feature/id
                                     :event/new-sequence-feature
                                     ::wssf/new-batch
                                     conform
                                     request))}})))

(comment
  (conform-to-range ::wssf/new-batch 10))
