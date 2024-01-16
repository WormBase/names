(ns wormbase.names.stats
  (:require
   [clojure.walk :as w]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [ring.util.http-response :refer [ok]]
   [wormbase.names.util :as wnu]
   [wormbase.specs.stats :as wsst]))

(defn ident-count [db ident]
  (d/q '[:find (count ?e) .
         :in $ ?ident
         :where
         [?e ?ident _]]
       db
       ident))

(defn summary [request]
  (let [enabled-ent-types (d/q '[:find [?et ...]
                                 :where
                                 [?e :wormbase.names/entity-type-enabled? true]
                                 [?e :db/ident ?et]]
                               (:db request))]
    (->> (conj enabled-ent-types :batch/id)
         (map (fn ident-to-count [ident]
                [(str (namespace ident)) (or (ident-count (:db request) ident) 0)]))
         (into {})
         (w/stringify-keys))))

(defn handle-summary [request]
  (-> (summary request)
      (ok)))

(def routes (sweet/routes
             (sweet/context "/stats" []
               :tags ["stats"]
               (sweet/resource
                {:get
                 {:summary "Counts of the number of entities in the system."
                  :x-name ::stats-summary-all
                  :responses (wnu/http-responses-for-read {:schema ::wsst/summary})
                  :handler handle-summary}}))))
