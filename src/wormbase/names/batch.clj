(ns wormbase.names.batch
  (:require
   [clj-uuid :as uuid]
   [compojure.api.sweet :as sweet]
   [ring.util.http-response :refer [bad-request bad-request! conflict created ok]]
   [wormbase.names.batch.gene :as wnbg]
   [wormbase.names.batch.generic :refer [info query-provenance]]
   [wormbase.names.batch.variation :as wnbv]
   [wormbase.names.batch.sequence-feature :as wnbsf]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.batch :as wsb]))

(def info-routes
  (sweet/routes
   (sweet/context "" []
     (sweet/GET "/:batch-id" request
       :summary "Information about a given batch operation."
       :responses (wnu/response-map ok {:schema ::wsb/info})
       :path-params [batch-id :- :batch/id]
       (info request batch-id wnp/pull-expr)))))

(def resources
  (sweet/context "/batch" []
    :tags ["batch"]
    (sweet/routes wnbg/routes
                  wnbv/routes
                  wnbsf/routes
                  info-routes)))

(def routes (sweet/routes resources))
