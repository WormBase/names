(ns org.wormbase.names.errhandlers
  (:require
   [clojure.spec.alpha :as s]
   [buddy.auth :refer [authenticated?]]
   [environ.core :as environ]
   [ring.util.http-response :as http-response])
  (:import
   (clojure.lang ExceptionInfo)
   (java.util.concurrent ExecutionException)))

(defn respond-bad-request [request data]
  (let [format (-> request
                   :compojure.api.request/muuntaja
                   :default-format)]
    (-> data
        (http-response/bad-request)
        (http-response/content-type format))))

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
      (f (if (= type :validation-error)
           (let [info* (ex-data err)
                 problems (if info*
                            (:problems info*))
                 info* (when problems
                         (assoc info* :problems problems))]
                                ;; (pr-str problems)))]
             info*)
           info)))))

(defn handle-validation-error [^Exception exc data request]
  (let [problems (get-in data [:data :problems])
        info (if problems
               (assoc data :problems problems)
               data)]
    (respond-bad-request request info)))

(defn handle-unexpected-error
  ([^Exception exc data request]
  ;; TODO: logging - ensure exceptions appear in the log/stdout.
  ;; TODO: remove constant comparison thing below (or true x)
  (if (environ/env :wb-ns-dev)
    (handle-unexpected-error exc)
    (http-response/internal-server-error data)))
  ([exc]
   (throw exc)))

(defn handle-txfn-error [^Exception exc data request]
  (let [txfn-err? (instance? ExecutionException exc)
        cause (.getCause exc)]
    (if (and txfn-err? (instance? ExceptionInfo cause))
      (handle-validation-error cause (ex-data cause) request)
      (handle-unexpected-error exc data request))))

(defn handle-request-validation
  ([^Exception exc data request]
   (println "handle-req-val:"
            (s/explain-data (:spec data) (:problems data)))
   (respond-bad-request request {:reason (:problems data)}))
  ([err]
   err))

(defn handle-unauthenticated [^Exception exc data request]
  (if-not (authenticated? request)
    (http-response/unauthorized "Access denied")
    (http-response/forbidden)))
