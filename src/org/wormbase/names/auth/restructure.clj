(ns org.wormbase.names.auth.restructure
  "Extends compojure-api letk syntax with custom restructuring for auth(z)."
  (:require
   [clojure.set :as set]
   [compojure.api.meta :refer [restructure-param]]
   [buddy.auth.accessrules :refer [wrap-access-rules]]
   [ring.util.http-response :as http-response]))

(defn require-role! [required roles]
  (if-not (seq (set/intersection required roles))
    (http-response/unauthorized!
     {:text "Missing required role"
      :required required
      :roles roles})))

(defmethod restructure-param :roles [_ roles acc]
  (update-in acc
             [:lets]
             into
             ['_ `(require-role!
                   ~roles
                   (get-in ~'+compojure-api-request+
                           [:identity :person/roles]))]))

