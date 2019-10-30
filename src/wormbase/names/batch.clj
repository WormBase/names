(ns wormbase.names.batch
  (:require
   [clj-uuid :as uuid]
   [compojure.api.sweet :as sweet]
   [ring.util.http-response :refer [ok]]
   [wormbase.names.batch.gene :as wnbg]
   [wormbase.names.batch.generic :as generic]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.batch :as wsb]))

(def summary-routes
  (sweet/routes
   (sweet/context "" []
     (sweet/GET "/:batch-id" request
       :summary "Summarise a given batch operation."
       :responses (wnu/response-map ok {:schema ::wsb/summary})
       :path-params [batch-id :- :batch/id]
       (generic/summary request batch-id wnp/pull-expr)))))

(def resources
  (sweet/context "/batch" []
    :tags ["batch"]
    (sweet/routes summary-routes
                  wnbg/routes
                  generic/routes)))

(def routes (sweet/routes resources))
