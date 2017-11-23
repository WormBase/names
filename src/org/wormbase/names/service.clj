(ns org.wormbase.names.service
  (:require
   [clojure.spec.alpha :as s]
   [compojure.api.coercion.schema :as cs]
   [compojure.api.exception :as ex]
   [compojure.api.middleware :as mw]
   [compojure.api.sweet :refer [api context resource]]
   [datomic.api :as d]
   [environ.core :as environ]
   [expound.alpha :as expound]
   [mount.core :as mount]
   [org.wormbase.db :as owndb]
   [org.wormbase.names.gene :as gene]
   [ring.middleware.gzip :as ring-gzip]
   [ring.util.http-response :as http-response])
  (:import
   (clojure.lang ExceptionInfo)
   (java.util.concurrent ExecutionException)))

(def ^:private swagger-validator-url
  "The URL used to validate the swagger JSON produced by the application."
  (if-let [validator-url (environ/env :swagger-validator-url)]
    validator-url
    "//online.swagger.io/validator"))

(defn- wrap-not-found
  "Fallback 404 handler."
  [request-handler]
  (fn [request]
    (let [response (request-handler request)]
      (or response
          (http-response/not-found
           {:reason "These are not the worms you're looking for"})))))

(defn- wrap-datomic
  "Annotates request with datomic connection and current db."
  [request-handler]
  (fn [request]
    (when-not (owndb/connected?)
      (mount/start))
    (let [conn owndb/conn]
      (-> request
          (assoc :conn conn :db (d/db conn))
          (request-handler)))))

(defn init
  "Entry-point for ring server initialization."
  []
  (mount/start))

(defn custom-exc-handler [f type]
  (fn [^Exception exc data request]
    ;; Exceptions thrown in a transaction function are wrapped
    ;; in a concurrent.ExecutionException -
    ;; override custom exc handler to be bad-request if the
    ;; cause was thrown with `exc-info`
    (let [txfn-err? (instance? ExecutionException exc)
          cause (.getCause exc)
          err (if (and txfn-err? (instance? ExceptionInfo cause))
                cause
                exc)
          info {:message (.getMessage err) :type type}]
      ;; If you can't find what the mistake is:
      ;; (println err)
      (f (if (= :type :validation-error)
           (let [info* (ex-data err)
                 problems (if info*
                            (:problems info*))
                 info* (when problems
                         (assoc info*
                                :problems (pr-str problems)))]
             info*)
           info)))))

(defn handle-validation-error [^Exception exc data request]
  (http-response/bad-request
   (let [problems (get-in data [:data :problems])
         info (if problems
                (assoc data
                       :problems
                       (expound/expound-str (:spec data) problems))
                data)]
     (ex-data exc))))

(defn handle-unexpected-error [^Exception exc data request]
  ;; TDB: logging - ensure exceptions appear in the log/stdout.
  (if (environ/env :wb-ns-dev)
    (throw exc)
    (http-response/internal-server-error data)))

(defn handle-txfn-error [^Exception exc data request]
  (let [txfn-err? (instance? ExecutionException exc)
        cause (.getCause exc)]
    (if (and txfn-err? (instance? ExceptionInfo cause))
      (handle-validation-error cause (ex-data cause) request)
      (handle-unexpected-error exc data request))))

(def app
  (api
   {:coercion :spec
    :exceptions
    {:handlers
     {ExecutionException handle-txfn-error
      :org.wormbase.db.schema/validation-error handle-validation-error
      ::ex/default handle-unexpected-error}}
    :swagger
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
     :middleware [ring-gzip/wrap-gzip
                  wrap-datomic
                  wrap-not-found]
     gene/routes)))
