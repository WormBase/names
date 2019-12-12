(ns wormbase.names.stats
  (:require
   [clojure.walk :as w]
   [datomic.api :as d]
   [ring.middleware.not-modified :as rmnm]
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
  (let [etag (some-> request :db d/basis-t wnu/encode-etag)]
    (-> (summary request)
        (ok)
        (wnu/add-etag-header-maybe etag))))

(def routes
  [["/stats"
    {:swagger {:tags ["stats"]}
     :get {:summary "Counts of the number of entities in the system."
           :x-name ::stats-summary-all
           :responses (wnu/http-responses-for-read {:schema ::wsst/summary})
           :handler handle-summary}}]])
