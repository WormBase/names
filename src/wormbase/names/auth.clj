(ns wormbase.names.auth
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.walk :as w]
   [clojure.tools.logging :as log]
   [buddy.auth :as auth]
   [buddy.auth.accessrules :as auth-access-rules]
   [buddy.auth.backends.token :as babt]
   [buddy.auth.middleware :as auth-mw]
   [compojure.api.meta :as capi-meta]
   [datomic.api :as d]
   [wormbase.names.agent :as wn-agent]
   [wormbase.names.util :as util]
   [wormbase.names.util :as wnu]
   [ring.middleware.defaults :as rmd]
   [ring.util.http-response :as http-response])
  (:import
   (com.google.api.client.googleapis.auth.oauth2 GoogleIdToken
                                                 GoogleIdToken$Payload
                                                 GoogleIdTokenVerifier$Builder)
   (com.google.api.client.http.javanet NetHttpTransport)
   (com.google.api.client.json.jackson2 JacksonFactory)))

(def ^:private net-transport (NetHttpTransport.))

(def ^:private json-factory (JacksonFactory.))

(def ^:private gapps-conf (:google-apps (util/read-app-config)))

(def ^:private token-verifier (.. (GoogleIdTokenVerifier$Builder. net-transport
                                                                  json-factory)
                                  (setAudience (->> (vals gapps-conf)
                                                    (map :client-id)
                                                    (apply list)))
                                  (build)))

(defn client-id [client-type]
  (-> gapps-conf client-type :client-id))

(defn verify-token-gapi [token]
  (some->> (.verify token-verifier token)
           (.getPayload)
           (into {})))

(defn verify-token [token]
  (try
    (when-let [gtoken-info (verify-token-gapi token)]
      (let [token-info (w/keywordize-keys gtoken-info)]
        (when (and
               token-info
               (wn-agent/identify token-info)
               (= (:hd token-info) "wormbase.org")
               (true? (:email_verified token-info)))
          token-info)))
    (catch IllegalArgumentException ex
      (log/error ex)
      nil)))

(defn identify [request token]
  (when-let [tok (verify-token token)]
    (let [email (:email tok)
          lur [:person/email email]
          db (:db request)]
      (if-let [person (d/entity db lur)]
        (merge tok (wnu/entity->map person))
        (log/warn (str "No person exists in db matching email: " email))))))

(def backend (babt/token-backend {:authfn identify}))

(defn wrap-auth
  [handler]
  (-> handler
      (auth-mw/wrap-authentication backend)
      (auth-mw/wrap-authorization backend)
      (rmd/wrap-defaults rmd/api-defaults)))

(defn authenticated? [req]
  (auth/authenticated? req))

(defn access-error [_ val]
  (http-response/unauthorized! val))

;; Middleware

(defn wrap-restricted [handler rule]
  (auth-access-rules/restrict handler {:handler rule
                                       :on-error access-error}))

(defn restrict-access [rule]
  (fn rsetricted [handler]
    (auth-access-rules/restrict handler {:handler rule
                                         :on-error access-error})))

(def restrict-to-authenticated (restrict-access auth/authenticated?))

;; restructuring predicates
(defn admin
  "compojure restrucring predicate.

  Requires that a map be present under `:identity` in the `request`,
  having a matching `:role`."
  [request]
  (and (authenticated? request)
       (#{:admin} (:role (:identity request)))))


;; "meta" restructuring

(defn require-role! [required roles]
  (if-not (seq (set/intersection required roles))
    (http-response/unauthorized!
     {:text "Missing required role"
      :required required
      :roles roles})))

(defmethod capi-meta/restructure-param :roles [_ roles acc]
  (update-in acc
             [:lets]
             into
             ['_ `(require-role!
                   ~roles
                   (get-in ~'+compojure-api-request+
                           [:identity :person/roles]))]))


(defn wrap-rule [handler rule]
  (-> handler
      (auth-access-rules/wrap-access-rules
       {:rules [{:pattern #".*"
                 :handler rule}]
        :on-error access-error})))

(defmethod capi-meta/restructure-param :auth-rules
  [_ rule acc]
  (update-in acc [:middleware] conj [wrap-rule rule]))
