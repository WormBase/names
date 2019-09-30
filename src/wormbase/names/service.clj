(ns wormbase.names.service
  (:require
   [clojure.string :as str]
   [buddy.auth :as auth]
   [compojure.api.middleware :as mw]
   [compojure.api.swagger :as swagger]
   [compojure.api.sweet :as sweet]
   [compojure.route :as route]
   [environ.core :as environ]
   [mount.core :as mount]
   [muuntaja.middleware :as mmw]
   [wormbase.db :as wdb]
   [wormbase.db.schema :as wdbs]
   [wormbase.names.auth :as wna]
   [wormbase.names.batch :as wn-batch]
   [wormbase.names.coercion] ;; coercion scheme
   [wormbase.names.entity :as wne]
   [wormbase.names.errhandlers :as wn-eh]
   [wormbase.names.gene :as wn-gene]
   [wormbase.names.person :as wn-person]
   [wormbase.names.recent :as wn-recent]
   [wormbase.names.response-formats :as wnrf]
   [wormbase.names.species :as wn-species]
   [wormbase.names.stats :as wn-stats]
   [ring.middleware.content-type :as ring-content-type]
   [ring.middleware.file :as ring-file]
   [ring.middleware.gzip :as ring-gzip]
   [ring.middleware.resource :as ring-resource]
   [ring.util.http-response :as http-response]))

(defn- wrap-not-found
  "Fallback 404 handler."
  [request-handler]
  (fn [request]
    (if-let [response (request-handler request)]
      response
      (cond
        (str/starts-with? (:uri request) "/api")
        (http-response/not-found {:message "Resource not found (fallback)"})

        :else
        (-> (http-response/resource-response "client_build/index.html")
            (http-response/content-type "text/html")
            (http-response/status 200))))))

(def ^:private swagger-validator-url
  "The URL used to validate the swagger JSON produced by the application."
  (if-let [validator-url (environ/env :swagger-validator-url)]
    validator-url
    "//online.swagger.io/validator"))

(def ^{:doc "Configuration for the Swagger UI."} swagger-ui
  {:ui "/api-docs"
   :spec "/swagger.json"
   :ignore-missing-mappings? false
   :data
   {:info
    {:title "WormBase name service"
     :description "Provides naming operations for WormBase entities."}
    ;; TODO: look up how to define securityDefinitions properly!
    ;;       will likely need to add some middleware such that the info
    ;;       can vary depending on the user-agent...
    ;;       i.e scripts will use Bearer auth, browser will use... (?)
    :securityDefinitions
    {:api_key
     {:type "apiKey"
      :name "x-apikey"
      :in "header"}}
    :tags
    [{:name "api"}
     {:name "feature"}
     {:name "gene"}
     {:name "variation"}
     {:name "person"}]}})

(defn wrap-static-resources [handler]
  (-> handler
      (ring-resource/wrap-resource "client_build")
      (ring-content-type/wrap-content-type)))

(def ^{:doc "The main application."} app
  (sweet/api
   {:formats wnrf/json
    :coercion :pure-spec
    :exceptions {:handlers wn-eh/handlers}
    :middleware [ring-gzip/wrap-gzip
                 wrap-static-resources
                 wdb/wrap-datomic
                 wna/wrap-auth
                 mmw/wrap-format
                 wrap-not-found]
    :swagger swagger-ui}
   (sweet/context "" []
     (sweet/context "/api" []
       :middleware [wna/restrict-to-authenticated]
       wn-species/routes
       wn-gene/routes
       wn-person/routes
       wn-recent/routes
       wn-batch/routes
       wn-stats/routes
       wne/routes))))

(defn init
  "Entry-point for ring server initialization."
  []
  (mount/start))
