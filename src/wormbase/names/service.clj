(ns wormbase.names.service
  (:require
   [clojure.string :as str]
   [buddy.auth :as auth]
   [environ.core :as environ]
   [mount.core :as mount]
   [muuntaja.core :as m]
   [muuntaja.middleware :as mmw]
   [wormbase.db :as wdb]
   [wormbase.db.schema :as wdbs]
   [wormbase.names.auth :as wna]
   [wormbase.names.batch :as wn-batch]
   [wormbase.names.batch.gene :as batch-gene]
   [wormbase.names.batch.generic :as batch-generic]
   [wormbase.names.coercion :as wnc]
   [wormbase.names.entity :as wne]
   [wormbase.names.errhandlers :as wneh]
   [wormbase.names.gene :as wng]
   [wormbase.names.person :as wn-person]
   [wormbase.names.recent :as wn-recent]
   [wormbase.names.response-formats :as wnrf]
   [wormbase.names.species :as wn-species]
   [wormbase.names.stats :as wn-stats]
   [reitit.ring :as ring]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [reitit.ring.coercion :as coercion]
   [reitit.dev.pretty :as pretty]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.parameters :as parameters]
   [ring.adapter.jetty :as raj]
   [ring.middleware.content-type :as ring-content-type]
   [ring.middleware.file :as ring-file]
   [ring.middleware.gzip :as ring-gzip]
   [ring.middleware.not-modified :as rmnm]
   [ring.middleware.params :as params-mw]
   [ring.middleware.resource :as ring-resource]
   [ring.util.http-response :as http-response]
   [reitit.ring.middleware.dev]))

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
     {:name "gene"}
     {:name "entity"}
     {:name "person"}]}})

(defn wrap-static-resources [handler]
  (-> handler
      (ring-resource/wrap-resource "client_build")
      (ring-content-type/wrap-content-type)))

(def routes
  ["" {:no-doc true}
   ["/swagger.json" {:get {:no-doc true
                           :swagger {:info {:title "WormBase Names service API."}}
                           :handler (swagger/create-swagger-handler)}}]])

(def router (ring/router
             [routes
              ["/api" {:coercion wnc/open-spec
                       :muuntaja m/instance}
               [wn-person/routes
                wng/routes
                wne/routes
                wn-recent/routes
                batch-gene/routes
                batch-generic/routes
                wn-batch/routes
                wn-stats/routes
                wn-species/routes]]]
             {:exception pretty/exception
              ;; :reitit.middleware/transform reitit.ring.middleware.dev/print-request-diffs
              :data {:middleware [swagger/swagger-feature
                                  ring-gzip/wrap-gzip
                                  wrap-static-resources
                                  parameters/parameters-middleware
                                  muuntaja/format-negotiate-middleware
                                  muuntaja/format-response-middleware
                                  wneh/exception-middleware
                                  muuntaja/format-request-middleware
                                  coercion/coerce-response-middleware
                                  coercion/coerce-request-middleware
                                  rmnm/wrap-not-modified
                                  wrap-not-found
                                  wdb/wrap-datomic
                                  wna/wrap-auth
                                  ]}}))

(def ^{:doc "The main application."} app
  (ring/ring-handler
   router
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/api-docs"
      :config {:validatorUri nil
               :operationSorter "alpha"}})
    (ring/create-resource-handler {:path "client_build"})
    (ring/create-default-handler))))

(mount/defstate server
  :start (raj/run-jetty app {:port (read-string (get environ/env :port "3000"))
                             :join? false})
  :stop (.stop server))

(defn -main
  "Entry-point for ring server initialization."
  [& args]
  (mount/start)
  (wdbs/install wdb/conn))
