(ns wormbase.names.batch
  (:require
   [clj-uuid :as uuid]
   [ring.util.http-response :refer [ok]]
   [wormbase.names.batch.gene :as wnbg]
   [wormbase.names.batch.generic :as generic]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.batch :as wsb]))

(def routes
  ["/batch" {:swagger {:tags ["batch"]}}
   ["/log/:batch-id" {:get {:summary "Summarise a given batch operation."
                            :responses (wnu/http-responses-for-read {:schema ::wsb/summary})
                            :parameters {:path {:batch-id :batch/id}}
                            :handler (fn [{{{:keys [batch-id]} :path} :parameters :as request}]
                                       (generic/summary request batch-id wnp/pull-expr))}}]])





