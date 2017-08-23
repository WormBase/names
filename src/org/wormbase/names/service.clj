(ns org.wormbase.names.service
  (:require
   [compojure.api.sweet :refer [api context resource]]
   [datomic.api :as d]
   [environ.core :as environ]
   [mount.core :as mount]
   [org.wormbase.db :as owndb]
   [org.wormbase.names.gene :as gene]
   [ring.middleware.gzip :as ring-gzip]
   [ring.util.http-response :refer [not-found ok]]))

(def ^:private swagger-validator-url
  "The URL used to validate the swagger JSON produced by the application."
  (if-let [validator-url (environ/env :swagger-validator-url)]
    validator-url
    "//online.swagger.io/validator"))

(def ^:private formats
  "The formats API endpoints used to consume/produce data."
  ["application/json"])

(defn- wrap-not-found
  "Fallback 404 handler."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (or response
          (not-found
           {:reason "These are not the worms you're looking for"})))))

(defn- wrap-datomic
  "Annotates request with datomic connection and current db."
  [handler]
  (fn [request]
    (let [conn owndb/conn]
      (handler (assoc request :conn conn :db (d/db conn))))))

(defn init
  "Entry-point for ring server initialization."
  []
  (mount/start))

(def app
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger.json"
     :data
     {:info
      {:title "Wormbase name service"
       :description "Provides naming operations for WormBase entities."}
      :tags
      [{:name "api" :description "Name service api"}
       {:name "gene", :description "Gene name ops"}
       {:name "feature" :description "Feature name ops"}
       {:name "variation" :description "Variation name ops"}]}}}
   (context "" []
     :middleware [;;ring-gzip/wrap-gzip
                  wrap-datomic
                  wrap-not-found]
     gene/routes)))
