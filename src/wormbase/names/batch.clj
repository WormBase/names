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
       :responses (wnu/response-map ok {:schema ::wsb/info})
       :path-params [batch-id :- :batch/id]
       (info request batch-id wnp/pull-expr)))))

(defn debug-txes [coll]
  (println "tx ids:")
  (prn coll)
  coll)

(defn recent-activities [request & {:keys [days-ago]
                                   :or {days-ago 60}}]
  (let [{conn :conn db :db} request
        now (jt/instant)
        start-t (jt/to-java-date now)
        end-t (->> (jt/days days-ago)
                   (jt/minus now)
                   (jt/to-java-date))]
    (->> (d/q '[:find [?tx ...]
                :in $ ?log ?start ?end
                :where
                [(tx-ids ?log ?start ?end)]
                [?tx :batch/id _ _]]
              db
              (d/log conn)
              start-t
              end-t)
         (debug-txes)
         (map (fn [tx]
                (wdb/pull db wnp/pull-expr tx)))
         (wnp/sort-events-by-when))))

(def resources
  (sweet/context "/batch" []
    :tags ["batch"]
    (sweet/routes wnbg/routes
                  wnbv/routes
                  wnbsf/routes
                  info-routes)))

(def routes (sweet/routes resources))
