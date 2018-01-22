(ns org.wormbase.names.oauth2
  (:require
   [clojure.string :as str]
   [clj-http.client :as http]
   [org.wormbase.names.util :as util]
   [ring.util.codec :as codec]
   [ring.util.request :as req]))

(defn- scopes [profile]
  (str/join " " (map name (:scopes profile))))

(defn- redirect-uri [profile request]
  (-> (req/request-url request)
      (java.net.URI/create)
      (.resolve (:redirect-uri profile))
      str))

(defn- authorize-uri [request state]
  (let [profile (:google (util/read-app-config))]
    (str (:authorize-uri profile)
         (if (.contains ^String (:authorize-uri profile) "?") "&" "?")
         (codec/form-encode {:response_type "code"
                             :client_id     (:client-id profile)
                             :redirect_uri  (redirect-uri profile request)
                             :scope         (scopes profile)
                             :state         state}))))

