(ns wormbase.names.auth
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [clojure.walk :as w]
   [buddy.auth :as auth]
   [buddy.auth.accessrules :as baa]
   [buddy.auth.backends.token :as babt]
   [buddy.auth.middleware :as auth-mw]
   [buddy.sign.compact :as bsc]
   [compojure.api.meta :as capi-meta]
   [datomic.api :as d]
   [ring.middleware.defaults :as rmd]
   [ring.util.http-response :as http-response]
   [wormbase.util :as wu]
   [wormbase.names.agent :as wn-agent])
  (:import
   (com.google.api.client.googleapis.auth.oauth2 GoogleIdTokenVerifier$Builder
                                                 GoogleIdToken)
   (com.google.api.client.json.webtoken JsonWebSignature)
   (com.google.api.client.http.javanet NetHttpTransport)
   (com.google.api.client.json.jackson2 JacksonFactory)))

(def ^:private net-transport (NetHttpTransport.))

(def ^:private json-factory (JacksonFactory.))

(def ^:private app-conf (wu/read-app-config))

(def ^:private gapps-conf (:google-apps app-conf))

(def ^:private token-verifier (.. (GoogleIdTokenVerifier$Builder. net-transport
                                                                  json-factory)
                                  (setAudience (->> (vals gapps-conf)
                                                    (map :client-id)
                                                    (apply list)))
                                  (build)))

(defn parse-token
  "Parse a Google ID token, returns a map containing the information associated with Google ID token.
  Returns nil if the token cannot be parsed."
  [^String token]
  (try
    (some->> (GoogleIdToken/parse json-factory token)
             (.getPayload)
             (into {})
             (w/keywordize-keys))
    (catch IllegalArgumentException _
      nil)))

(defn client-id
  "Returns the Google client-id from application configuration give the client type."
  [client-type]
  (-> gapps-conf client-type :client-id))

(defn verify-token-gapi
  "Return a truthy value iif the token is valid."
  [token]
  (some->> (.verify token-verifier token)
           (.getPayload)))

(defn verify-token
  "Verify the token via Google API(s).
  Returns a map containing the information held in the Google ID Token."
  [^String token]
  (try
    (when-let [gtoken (verify-token-gapi token)]
      (when (and
             gtoken
             (wn-agent/identify gtoken)
             (= (.getHostedDomain gtoken) "wormbase.org")
             (true? (.getEmailVerified gtoken)))
        (w/keywordize-keys (into {} gtoken))))
    (catch Exception exc
      (log/error exc "Invalid token supplied"))))

(defn query-person
  "Query the database for a WormBase person, given the schema attribute
  and token associated with authentication.

  Return a map containing the information about a person, omitting the auth token."
  [db ident auth-token]
  (let [person (d/pull db '[*] [ident auth-token])]
    (when (:db/id person)
      (dissoc person :person/auth-token))))

(defn sign-token
  "Sign a token using a key from the application configuration."
  [auth-token-conf token]
  (bsc/sign (str token) (:key auth-token-conf)))

(defrecord Identification [token-info person])

(defn verified-person
  "Return a person from the database having a matching stored token."
  [db auth-token-conf parsed-token]
  (let [email (:email parsed-token)]
    (if-let [{stored-token :person/auth-token} (d/pull db
                                                       '[:person/auth-token]
                                                       [:person/email email])]

      (when-let [unsigned-token (try
                                  (bsc/unsign stored-token (:key auth-token-conf)
                                              (select-keys auth-token-conf [:max-age]))
                                  (catch Exception ex
                                    (log/debug "Failed to unsigned stored token"
                                               {:stored-token stored-token
                                                :key (:key auth-token-conf)})))]
        (query-person db :person/email email)))))

(defn identify
  "Identify the wormbase user associated with request.
   Conditionally associates the authentication token with the user in the database.
   Return an Identificaiton record."
  [request ^String token]
  (let [auth-token-conf (:auth-token app-conf)
        parsed-token (try
                       (parse-token token)
                       (catch Exception ex
                         (log/error ex "Malformed token")))
        db (:db request)]
    (when parsed-token
      (if-let [person (verified-person db auth-token-conf parsed-token)]
        (map->Identification {:token-info parsed-token :person person})
        (when-let [tok (verify-token token)]
          (if-let [person (query-person db :person/email (:email tok))]
            (let [auth-token (sign-token auth-token-conf tok)
                  tx-result @(d/transact-async (:conn request)
                                               [[:db/add
                                                 (:db/id person)
                                                 :person/auth-token
                                                 auth-token]])]
              (map->Identification {:token-info parsed-token :person person}))
            (log/warn "NO PERSON FOUND IN DB:" tok)))))))

(def backend (babt/token-backend {:authfn identify}))

(defn wrap-auth
  "Ring middleware for applying authentication scheme."
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
  (baa/restrict handler {:handler rule
                         :on-error access-error}))

(defn restrict-access [rule]
  (fn restricted [handler]
    (baa/restrict handler {:handler rule
                           :on-error access-error})))

(def restrict-to-authenticated (restrict-access auth/authenticated?))

(defn admin
  "compojure restrucring predicate.

  Requires that a map be present under `:identity` in the `request`,
  having a matching `:role`."
  [request]
  (and (authenticated? request)
       (#{:admin} (:role (:identity request)))))

(defn require-role! [required request]
  (let [roles (some-> request :identity :person :person/roles)]
    (if-not (seq (set/intersection (set required) (set roles)))
      (http-response/unauthorized!
       {:text "Missing required role"
        :required required
        :roles roles}))))
