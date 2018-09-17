(ns wormbase.names.service
  (:require
   [clojure.string :as str]
   [buddy.auth :as auth]
   [compojure.api.middleware :as mw]
   [compojure.api.sweet :as sweet]
   [compojure.route :as route]
   [environ.core :as environ]
   [mount.core :as mount]
   [muuntaja.core :as muuntaja]
   [muuntaja.middleware :as mmw]
   [wormbase.db :as wdb]
   [wormbase.names.auth :as wna]
   [wormbase.names.coercion] ;; coercion scheme
   [wormbase.names.errhandlers :as wn-eh]
   [wormbase.names.gene :as wn-gene]
   [wormbase.names.person :as wn-person]
   [ring.middleware.content-type :as ring-content-type]
   [ring.middleware.file :as ring-file]
   [ring.middleware.gzip :as ring-gzip]
   [ring.middleware.resource :as ring-resource]
   [ring.util.http-response :as http-response]))

(def ^{:private true
       :doc "Request/Response format configuration"} mformats
  (muuntaja/create))

(defn- wrap-not-found
  "Fallback 404 handler."
  [request-handler]
  (fn [request]
    (if-let [response (request-handler request)]
      response
      (cond
        (str/starts-with? (:uri request) "/api")
        (http-response/not-found {:message "Resource not found"})

        :else
        (-> (http-response/resource-response "client_build/index.html")
            (http-response/content-type "text/html")
            (http-response/status 200))))))

(defn decode-content [mime-type content]
  (muuntaja/decode mformats mime-type content))

(defn encode-content [mime-type content]
  (slurp (muuntaja/encode mformats mime-type content)))

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
    {:title "WormBase name service"
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

(defn wrap-static-resources [handler]
  (ring-resource/wrap-resource handler "client_build"))

(def ^{:doc "The main application."} app
  (sweet/api
   {:formats mformats
    :coercion :pure-spec
    :middleware [ring-gzip/wrap-gzip
                 wrap-static-resources
                 wrap-not-found
                 wdb/wrap-datomic
                 wna/wrap-auth
                 mmw/wrap-format]
    :exceptions {:handlers wn-eh/handlers}
    :swagger swagger-ui}
   (sweet/context "" []
     (sweet/context "/api" []
       wn-person/routes
       wn-gene/routes))))

(defn init
  "Entry-point for ring server initialization."
  []
  (mount/start))
