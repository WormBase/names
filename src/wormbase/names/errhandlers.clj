(ns wormbase.names.errhandlers
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [buddy.auth :refer [authenticated?]]
   [compojure.api.exception :as ex]
   [datomic.api :as d]
   [environ.core :as environ]
   [expound.alpha :as expound]
   [muuntaja.core :as mc]
   [phrase.alpha :as ph]
   [ring.util.http-response :refer [bad-request
                                    conflict
                                    content-type
                                    forbidden
                                    internal-server-error
                                    not-found
                                    unauthorized]]
   [wormbase.db :as wdb]
   [wormbase.ids.batch :as wbids-batch]
   [wormbase.names.gene :as wn-gene]
   [wormbase.names.util :as wnu]
   [wormbase.names.response-formats :as wnrf])
  (:import
   (clojure.lang ExceptionInfo)
   (java.util.concurrent ExecutionException)))

(declare handlers)

;; Generates a human-readable error message
;; when there are missing required keys.
(ph/defphraser #(contains? % kw)
  [_ _ kw]
  (format "%s is required" (name kw)))

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

(defmethod parse-exc-message :db.error/nil-value
  [exc]
  (format "Cannot accept nil-value %s" (ex-data exc)))

(defmethod parse-exc-message :db.error/not-an-entity
  [exc]
  (let [ent (some-> exc ex-data :entity)]
    (if (keyword? ent)
      (format "Ident %s does not exist" ent)
      (apply format "Entity attribute %s with identifier '%s' does not exist" ent))))

(defmethod parse-exc-message :db.error/unique-conflict
  [exc]
  (let [msg (.getMessage exc)
        [k v] (->> msg
                   (re-find #"Unique conflict: :(.*), value: (.*) already*")
                   rest)
        [ident v] [(keyword k) v]
        ent-ns (namespace ident)
        id-ident (keyword ent-ns "id")
        id (-> (d/db wdb/conn)
               (d/pull [id-ident] [ident v]) id-ident)]
    (format "%s with %s identifier '%s' is already stored against %s."
            (str/capitalize ent-ns)
            (name ident)
            v
            id)))

(defmethod parse-exc-message :default
  [exc]
  (throw exc))

(defn- prettify-spec-error-maybe [spec data]
  (try
    (expound/expound-str spec (:value data))
    (catch Exception ex
      (println "UI fails to render the following:")
      (pr-str
       {:data (pr-str (:value data))
        :message "Unable to determine spec error."})
      {:data (pr-str (:value data))
        :message "Unable to determine spec error."})))

(defn handle-validation-error
  [^Exception exc data request & {:keys [message]}]
  (let [data* (dissoc data :request :spec :coercion :in)
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

(defn handle-missing [^Exception exc data request]
  (when (some-> data :entity keyword?)
    (throw (ex-info (format "Schema %s not installed!" (:entity data))
                    {:ident (:entity data)})))
  (if-let [lookup-ref (:entity data)]
    (let [msg (apply format "%s '%s' does not exist" lookup-ref)]
      (respond-missing request (assoc-error-message data exc :message msg)))
    (respond-missing request (assoc-error-message data exc))))

(defn handle-db-conflict [^Exception exc data request]
  (respond-conflict request (assoc-error-message data exc)))

(defn handle-db-unique-conflict [^Exception exc data request]
  (let [uc-err (parse-exc-message exc)
        body (assoc-error-message data exc :message uc-err)]
    (respond-conflict request body)))

(defn handle-unexpected-error
  ([^Exception exc data request]
   (if-let [db-err (:db/error data)]
     (if-let [db-err-handler (db-err handlers)]
       (db-err-handler exc data request)
       (handle-unexpected-error exc))
     (throw exc)))
  ([exc]
   (log/fatal "Unexpected errror" exc)
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
  [^Exception exc data request]
  (let [context {:topic (-> request :uri (str/split #"/") last)}
        {spec :spec value :value} data
        phrased (wnu/phrase-all context spec value)]
    (if (> (count phrased) 1)
      (handle-validation-error
       exc
       (assoc data :errors phrased)
       request
       :message "One or more problems found.")
      (handle-validation-error
       exc
       data
       request
       :message (ph/phrase-first {} spec value)))))

(defn handle-unauthenticated [^Exception exc data request]
  (if-not (authenticated? request)
    (unauthorized "Access denied")
    (forbidden)))

(defn handle-db-error [^Exception exc data request]
  (if-let [err-handler ((:db/error data) handlers)]
    (err-handler exc data request)
    (handle-db-conflict exc data request)))

(defn handle-batch-errors [^Exception exc data request]
  (try
    (let [batch-errors (:errors data)
          err-data {:errors (map
                             (fn [batch-error]
                               (let [err-type (:error-type batch-error)
                                     ed (if-let [err-handler (get handlers err-type)]
                                          (err-handler (:exc batch-error)
                                                       (ex-data (:exc batch-error))
                                                       request)
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

(def ^{:doc "Error handler dispatch map for the compojure.api app"}
  handlers
  {;;;;; Spec validation errors
   ::ex/request-validation handle-request-validation
   :user/validation-error handle-validation-error
   ::wn-gene/validation-error handle-validation-error
   ::wdb/validation-error handle-validation-error
   ExceptionInfo handle-validation-error

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
   ::ex/default handle-unexpected-error})
