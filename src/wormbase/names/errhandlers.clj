(ns wormbase.names.errhandlers
  (:require
   [clojure.spec.alpha :as s]
   [clojure.tools.logging :as log]
   [buddy.auth :refer [authenticated?]]
   [datomic.api :as d]
   [environ.core :as environ]
   [expound.alpha :as expound]
   [muuntaja.core :as mc]
   [reitit.coercion :as rc]
   [reitit.ring.middleware.exception :as rrme]
   [ring.util.http-response :refer [bad-request
                                    conflict
                                    content-type
                                    forbidden
                                    internal-server-error
                                    not-found
                                    unauthorized] :as ru-hp]
   [wormbase.db :as wdb]
   [wormbase.ids.batch :as wbids-batch]
   [wormbase.names.gene :as wn-gene]
   [wormbase.names.response-formats :as wnrf])
  (:import
   (clojure.lang ExceptionInfo)
   (java.util.concurrent ExecutionException)))

(declare handlers)

(defn respond-with [response-fn request data]
  (let [fmt (mc/default-format (or (:compojure.api.request/muuntaja request)
                                   wnrf/json))]
    (response-fn data)))

(def respond-bad-request (partial respond-with bad-request))

(def respond-conflict (partial respond-with conflict))

(def respond-missing (partial respond-with not-found))

(defn assoc-error-message [data exc & {:keys [message]}]
  (let [msg (or message (.getMessage exc) "No reason given")]
    (assoc data :message msg)))

(defmulti parse-exc-message (fn [exc]
                              (-> exc ex-data :db/error keyword)))

(defmethod parse-exc-message :db.error/nil-value [exc]
  (format "Cannot accept nil-value %s" (ex-data exc)))

(defmethod parse-exc-message :db.error/not-an-entity [exc]
  (let [ent (some-> exc ex-data :entity)]
    (if (keyword? ent)
      (format "Ident %s does not exist" ent)
      (apply format "Entity attribute %s with identifier '%s' does not exist" ent))))

(defmethod parse-exc-message :db.error/unique-conflict [exc]
  (let [msg (.getMessage exc)
        [k v] (->> msg
                   (re-find #"Unique conflict: :(.*), value: (.*) already*")
                   rest)
        [ident v] [(keyword k) v]]
    (format "Entity with %s identifier '%s' is already stored." (str ident) v)))

(defmethod parse-exc-message :default [exc]
  (throw exc))

(defn- prettify-spec-error-maybe [spec data]
  (try
    (expound/expound-str spec (:value data))
    (catch Exception ex
      {:data (pr-str (:value data))
       :message "Unable to determine spec error."})))

(defn handle-validation-error
  [exc request & {:keys [message]}]
  (let [data (ex-data exc)
        data* (dissoc data :request :spec :coercion :in)
        spec (:spec data)
        info (if (s/spec? spec)
               (if-let [problems (some-> data* :problems)]
                 (assoc data* :problems (prettify-spec-error-maybe spec data*))
                 data*)
               (-> data*
                   (update :type (fn [x]
                                   (if (keyword? x)
                                     (name x)
                                     x)))))
        body (assoc-error-message info exc :message message)]
    (respond-bad-request request body)))

(defn handle-missing [exc request]
  (let [data (ex-data exc)]
    (when (some-> data :entity keyword?)
      (throw (ex-info (format "Schema %s not installed!" (:entity data))
                      {:ident (:entity data)})))
    (if-let [lookup-ref (:entity data)]
      (let [msg (apply format "%s '%s' does not exist" lookup-ref)]
        (respond-missing request (assoc-error-message data exc :message msg)))
      (respond-missing request (assoc-error-message data exc)))))

(defn handle-db-conflict [exc request]
  (respond-conflict request (assoc-error-message (ex-data exc) exc)))

(defn handle-db-unique-conflict
  [exc request]
  (let [data (ex-data exc)
        uc-err (parse-exc-message exc)
        body (assoc-error-message data exc :message uc-err)]
    (respond-conflict request body)))

(defn handle-unexpected-error
  ([exc request]
   (let [data (ex-data exc)]
     (if-let [db-err (:db/error data)]
       (if-let [db-err-handler (db-err handlers)]
         (db-err-handler exc request)
         (handle-unexpected-error exc))
       (throw exc))))
  ([exc]
   (log/fatal "Unexpected errror" exc)
   (throw exc)))

(defn handle-txfn-error [exc request]
  (let [txfn-err? (instance? ExecutionException exc)
        cause (.getCause exc)
        cause-data (ex-data cause)
        err-type (:type cause-data)
        db-error (:db/error cause-data)
        err-handler-key (or err-type db-error)
        err-type-dispatch (dissoc handlers ExecutionException)]
    (if (and txfn-err? err-handler-key)
      (if-let [err-handler (get err-type-dispatch err-handler-key)]
        (err-handler cause request)
        (do
          (log/fatal (str "Could not find error handler for:" exc
                          "using lookup key:" err-handler-key))
          (throw exc)))
      (do
        (when (seq (remove nil? ((juxt :dev :test) environ/env)))
          (log/debug "TXFN-ERROR?:" txfn-err?
                     "HANDLER KEY:" err-handler-key)
          (log/debug "EXC_DATA?: " (ex-data exc))
          (log/debug "Message?: " (.getMessage exc))
          (log/debug "Cause?" (.getCause exc))
          (log/debug "Cause data?:" (ex-data (.getCause exc))))
        (handle-unexpected-error exc request)))))

(defn handle-request-validation
  [exc request]
  (let [data (ex-data exc)]
    (handle-validation-error
     exc
     data
     request
     :message "Request validation failed")))

(defn handle-unauthenticated [exc request]
  (let [data (ex-data exc)]
    (if-not (authenticated? request)
      (unauthorized "Access denied")
      (forbidden))))

(defn handle-db-error [exc request]
  (let [data (ex-data  exc)]
    (if-let [err-handler ((:db/error data) handlers)]
      (err-handler exc request)
      (handle-db-conflict exc request))))

(defn handle-batch-errors [exc request]
  (try
    (let [data (ex-data exc)
          batch-errors (:errors data)
          err-data {:errors (map
                             (fn [batch-error]
                               (let [err-type (:error-type batch-error)
                                     ed (if-let [err-handler (get handlers err-type)]
                                          (err-handler (:exc batch-error) request)
                                          {:body {:error-type err-type
                                                  :message (-> batch-error :exc parse-exc-message)}})]
                                 (:body ed)))
                             batch-errors)}]
      (respond-conflict request
                        (assoc-error-message err-data
                                             exc
                                             :message "processing errors occurred")))
    (catch Exception exc
      (handle-unexpected-error exc))))

(defn coercion-error-handler [status]
  (let [printer (expound/custom-printer {:theme :figwheel-theme, :print-specs? false})
        handler (rrme/create-coercion-handler status)]
    (fn [exception request]
      (printer (-> exception ex-data :problems))
      (println "HERE IN COERCION-ERROR-HANDLER status is" status)
      (let [result (handler exception request)]
        (println "ERROR HANDLER RESULT IN COERCION ERR HANDLING:")
        (prn result)
        (assoc-in result [:body :message] "The request was invalid")))))


(def handlers
  {:user/validation-error handle-validation-error
   ::wn-gene/validation-error handle-validation-error
   ::wdb/validation-error handle-validation-error


   ::rc/request-coercion (coercion-error-handler 400)
   ::rc/response-coercion (coercion-error-handler 500)
   ::ru-hp/response (:reitit.ring/response rrme/default-handlers)

   ;; ???
   ;; ExceptionInfo handle-validation-error

     ;;;;;  Exceptions raised within a transaction function are handled
   ExecutionException handle-txfn-error

   ;; App db errors
   ::wdb/conflict handle-db-conflict
   ::wdb/missing handle-missing

   ;; Datomic db exceptions
   :db/error handle-db-error
   :db.error/datoms-conflict handle-db-conflict
   :db.error/nil-value handle-unexpected-error
   :db.error/not-an-entity handle-missing
   :db.error/unique-conflict handle-db-unique-conflict
   datomic.impl.Exceptions$IllegalArgumentExceptionInfo handle-txfn-error

   ;; Batch errors, may contain multiple errors types from above
   ::wbids-batch/db-errors handle-batch-errors

   ;; Something else

   ;; TODO: find equivilent to compojure.api.exception
   ;; ::rrme/default handle-unexpected-error
   })

(def ^{:doc "Custom error handler dispatch map. Keys match the :type of (ex-data exc) in an exception."}
  exception-middleware
  (rrme/create-exception-middleware
   (merge rrme/default-handlers handlers)))
