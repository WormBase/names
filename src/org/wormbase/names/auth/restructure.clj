(ns org.wormbase.names.auth.restructure
  "Extends compojure-api letk syntax with custom restructuring for auth(z)."
  (:require
   [clojure.set :as set]
   [compojure.api.meta :refer [restructure-param]]
   [buddy.auth.accessrules :refer [wrap-access-rules]]
   [ring.util.http-response :refer [unauthorized unauthorized!]]))

(defn access-error [req val]
  (println "Unauthorized! REQ HEADERS WERE:" (:headers req))
  (unauthorized val))

(defn wrap-rule [handler rule]
  (wrap-access-rules handler {:rules [{:pattern #".*"
                                       :handler rule}]
                              :on-error access-error}))

(defmethod restructure-param :auth-rules
  [_ rule acc]
  (update-in acc [:middlewares] conj [wrap-rule rule]))

(defmethod restructure-param :current-user
  [_ binding acc]
  (update-in acc [:letks] into [binding `(:identity ~'+compojure-api-request+)]))


(defn require-role! [required roles]
  (if-not (seq (set/intersection required roles))
    (unauthorized! {:text "missing role", :required required, :roles roles})))

(defmethod restructure-param :roles [_ roles acc]
  (update-in
   acc
   [:lets]
   into ['_ `(require-role! ~roles (:roles ~'+compojure-api-request+))]))
