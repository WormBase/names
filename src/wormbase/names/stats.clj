(ns wormbase.names.stats
  (:require
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [ring.middleware.not-modified :as rmnm]
   [ring.util.http-response :refer [ok]]
   [wormbase.names.util :as wnu]
   [wormbase.specs.stats :as wsst]))

(defn ident-count [db ident]
  (d/q '[:find (count ?e) .
         :in $ ?ident
         :where [?e ?ident _]]
       db
       ident))

(defn summary [request]
  (into {}
        (map (fn ident-to-count [ident]
               [ident (or (ident-count (:db request) ident) 0)])
             [:gene/id :variation/id :sequence-feature/id :batch/id])))

(defn handle-summary [request]
  (let [etag (some-> request :db d/basis-t wnu/encode-etag)]
    (-> (summary request)
        (ok)
        (wnu/add-etag-header-maybe etag))))

(def routes (sweet/routes
             (sweet/context "/stats" []
               :middleware [rmnm/wrap-not-modified]
               :tags ["stats"]
               (sweet/resource
                {:get
                 {:summary "Counts of the number of entities in the system."
                  :x-name ::stats-summary-all
                  :responses (wnu/response-map ok {:schema ::wsst/summary})
                  :handler handle-summary}}))))
