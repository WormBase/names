(ns wormbase.names.errhandlers
  (:require
   [clojure.spec.alpha :as s]
   [clojure.tools.logging :as log]
   [buddy.auth :refer [authenticated?]]
   [compojure.api.exception :as ex]
   [environ.core :as environ]
   [wormbase.db :as wdb]
   ;;   TODO: better API error messages
   ;;   [phrase.alpha :as phrase]
   [ring.util.http-response :as http-response]
   [wormbase.names.gene :as wn-gene])
  (:import
   (clojure.lang ExceptionInfo)
   (java.util.concurrent ExecutionException)))

(declare handlers)

(defn respond-with [response-fn request data]
  (let [format (-> request
                   :compojure.api.request/muuntaja
                   :default-format)]
    (-> data
        (response-fn)
        (http-response/content-type format))))

(def respond-bad-request (partial respond-with http-response/bad-request))

(def respond-conflict (partial respond-with http-response/conflict))

(def respond-missing (partial respond-with http-response/not-found))

(defn custom-exc-handler [f type]
  (fn [^Exception exc data request]
    ;; Exceptions thrwn in a transaction function are wrapped
    ;; in a concurrent.ExecutionException -
    ;; override custom exc handler to be bad-request if the
    ;; cause was thrwn with `exc-info`
    (let [txfn-err? (instance? ExecutionException exc)
          cause (.getCause exc)
          err (if (and txfn-err? (instance? ExceptionInfo cause))
                cause
                exc)
          info {:message (.getMessage err) :type type}]
      (f (if (or (= type :validation-error) (= (name type) "validation-error"))
           (let [info* (ex-data err)
                 problems (if info*
                            (:problems (str info*)))
                 info* (when problems
                         (assoc info* :problems problems))]
                                ;; (pr-str problems)))]
             info*)
           info)))))

(defn assoc-error-message [data exc]
  (if-let [msg (.getMessage exc)]
    (assoc data :message msg)
    data))

(defn handle-validation-error [^Exception exc data request]
  (let [problems (get-in data [:data :problems])
        info (if problems
               (assoc data :problems (str problems))
               data)]
    (respond-bad-request request (assoc-error-message info exc))))

(defn handle-missing [^Exception exc data request]
  (respond-missing request (assoc-error-message data exc)))

(defn handle-db-conflict [^Exception exc data request]
  (respond-conflict request (assoc-error-message data exc)))

(defn handle-unexpected-error
  ([^Exception exc data request]
   (if-not (empty? ((juxt :test :dev) environ/env))
     (handle-unexpected-error exc)
     (http-response/internal-server-error data)))
  ([exc]
   (throw exc)))

(defn handle-txfn-error [^Exception exc data request]
  (let [txfn-err? (instance? ExecutionException exc)
        cause (.getCause exc)
        cause-data (ex-data cause)
        err-type (:type cause-data)
        db-error (:db/error cause-data)
        err-handler-key (or err-type db-error)
        err-type-dispatch (dissoc handlers ExecutionException)]
    (if (and txfn-err? err-handler-key)
      (if-let [err-handler (get err-type-dispatch err-handler-key)]
        (err-handler cause cause-data request)
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
        (handle-unexpected-error exc data request)))))

(defn handle-request-validation
  ([^Exception exc data request]
   (respond-bad-request request {:message (.getMessage exc)
                                 :problems (str (:problems data))}))
  ([err]
   err))

(defn handle-unauthenticated [^Exception exc data request]
  (if-not (authenticated? request)
    (http-response/unauthorized "Access denied")
    (http-response/forbidden)))

(def ^{:doc "Error handler dispatch map for the compojure.api app"} handlers
  {;; Spec validation errors
   ::ex/request-validation handle-request-validation ;; c-api
   :user/validation-error handle-validation-error
   ::wn-gene/validation-error handle-validation-error
   ::wdb/validation-error handle-validation-error
   ExceptionInfo handle-validation-error

   ;; Exceptions raised within a transaction function are handled
   ExecutionException handle-txfn-error

   ;; App db errors
   ::wdb/conflict handle-db-conflict
   ::wdb/missing handle-missing

   ;; Datomic db exceptions
   :db.error/not-an-entity handle-missing
   :db/error handle-db-conflict
   :db.error/nil-value handle-unexpected-error

   ;; TODO: this shouldn't really be here...spec not tight enough?
   datomic.impl.Exceptions$IllegalArgumentExceptionInfo handle-txfn-error

   ;; Something else
   ::ex/default handle-unexpected-error})
