(ns wormbase.names.service
  (:require
   [clojure.string :as str]
   [buddy.auth :as auth]
   [compojure.api.middleware :as mw]
   [compojure.api.sweet :as sweet]
   [compojure.route :as route]
   [environ.core :as environ]
   [mount.core :as mount]
   [muuntaja.core :as m]
   [muuntaja.core :as muuntaja]
   [wormbase.db :as wdb]
   [wormbase.names.auth :as wn-auth]
   [wormbase.names.auth.restructure] ;; Included for side effects
   [wormbase.names.errhandlers :as wn-eh]
   [wormbase.names.gene :as wn-gene]
   [wormbase.names.person :as wn-person]
   [ring.middleware.content-type :as ring-content-type]
   [ring.middleware.file :as ring-file]
   [ring.middleware.gzip :as ring-gzip]
   [ring.middleware.resource :as ring-resource]
   [ring.util.http-response :as http-response]))

(def default-format "application/json")

(def ^{:private true
       :doc "Request/Response format configuration"} mformats
  (muuntaja/create
    (muuntaja/select-formats
      muuntaja/default-options
      ["application/json"
       "application/transit+json"
       "application/edn"])))

(defn- wrap-not-found
  "Fallback 404 handler."
  [request-handler]
  (fn [request]
    (let [response (request-handler request)]
      (cond
        response
        response
        
        (str/starts-with? (:uri request) "/api")
        (-> {:reason "Resource not found"}
            (http-response/not-found)
            (http-response/content-type default-format))

        :otherwise-redirect-to-client-app
        (-> (http-response/resource-response "client_build/index.html")
            (http-response/content-type "text/html"))))))

(defn decode-content [mime-type content]
  (muuntaja/decode mformats mime-type content))

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
   {;; :basePath "/api"
    :info
    {:title "Wormbase name service"
     :description "Provides naming operations for WormBase entities."}

    ;; TODO: look up how to define securityDefinitions properly!
    ;;       will likely need to add some middleware such that the info
    ;;       can vary depending on the user-agent...
    ;;       i.e scripts will use Bearer auth, browser will use... (?)
    :securityDefinitions
    {:login
     {:type "http"
      :scheme "bearer"}}
    :tags
    [{:name "api"}
     {:name "feature"}
     {:name "gene"}
     {:name "variation"}
     {:name "person"}]}})

(def ^{:doc "The main application."} app
  (sweet/api
   {:coercion :spec
    :formats mformats
    :middleware [ring-gzip/wrap-gzip
                 wdb/wrap-datomic
                 wn-auth/wrap-auth
                 wrap-not-found]
    :exceptions {:handlers wn-eh/handlers}
    :swagger swagger-ui}
   (sweet/context "" []
     ;; TODO: is it right to be
     ;; repating the authorization and auth-rules params below so that
     ;; the not-found handler doesn't raise validation error?
     (sweet/context "/api" []
       wn-person/routes
       wn-gene/routes))))

(defn init
  "Entry-point for ring server initialization."
  []
  (mount/start))
