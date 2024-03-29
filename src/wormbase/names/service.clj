(ns wormbase.names.service
  (:require
   [clojure.string :as str]
   [compojure.api.sweet :as sweet]
   [environ.core :as environ]
   [mount.core :as mount]
   [muuntaja.middleware :as mmw]
   [wormbase.db :as wdb]
   [wormbase.db.schema :as wdbs]
   [wormbase.names.auth :as wn-auth]
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
   [ring.adapter.jetty :as raj]
   [ring.middleware.content-type :as ring-content-type]
   [ring.middleware.gzip :as ring-gzip]
   [ring.middleware.not-modified :as rmnm]
   [ring.middleware.resource :as ring-resource]
   [ring.util.http-response :as http-response]
   [ring.util.response :as ring-response]))

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
        (-> (ring-response/resource-response "client_build/index.html")
            (ring-response/content-type "text/html")
            (ring-response/status 200))))))

(def ^:private swagger-validator-url
  "The URL used to validate the swagger JSON produced by the application."
  (if-let [validator-url (environ/env :swagger-validator-url)]
    validator-url
    "//online.swagger.io/validator"))

(def ^{:doc "Configuration for the Swagger UI."} swagger-ui
  {:ui "/api-docs"
   :spec "/swagger.json"
   :validatorUrl swagger-validator-url
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
    :middleware [wrap-static-resources
                 wdb/wrap-datomic
                 wn-auth/wrap-auth
                 mmw/wrap-format
                 rmnm/wrap-not-modified
                 wrap-not-found
                 ring-gzip/wrap-gzip]
    :swagger swagger-ui}
   (sweet/context "" []
     (sweet/context "/api" []
       :middleware [wn-auth/restrict-to-authenticated]
       wn-auth/routes
       wn-species/routes
       wn-gene/routes
       wn-person/routes
       wn-recent/routes
       wn-batch/routes
       wn-stats/routes
       wne/routes))))

(declare server)

(mount/defstate server
  :start (raj/run-jetty app {:port (read-string (get environ/env :port "3000"))
                             :join? false})
  :stop (.stop server))

(defn -main
  "Entry-point for ring server initialization."
  [& _]
  (mount/start)
  (wdbs/ensure-schema wdb/conn))
