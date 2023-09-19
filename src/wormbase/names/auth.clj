(ns wormbase.names.auth
  (:require [buddy.auth :as auth]
            [buddy.auth.accessrules :as baa]
            [buddy.auth.backends.token :as babt]
            [buddy.auth.middleware :as auth-mw]
            [buddy.sign.compact :as bsc]
            [clojure.tools.logging :as log]
            [clojure.walk :as w]
            [datomic.api :as d]
            [environ.core :as environ]
            [ring.middleware.defaults :as rmd]
            [ring.util.http-response :as http-response]
            [wormbase.util :as wu])
  (:import (com.google.api.client.auth.oauth2 TokenResponseException)
           (com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeTokenRequest GoogleIdToken GoogleIdTokenVerifier$Builder)
           (com.google.api.client.http.javanet NetHttpTransport)
           (com.google.api.client.json.jackson2 JacksonFactory)))

(def ^:private net-transport (NetHttpTransport.))

(def ^:private json-factory (JacksonFactory.))

(def ^:private app-conf (wu/read-app-config))

(def ^:private token-verifier (.. (GoogleIdTokenVerifier$Builder. net-transport
                                                                  json-factory)
                                  (setAudience (->> (get environ/env :api-google-oauth-client-id)
                                                    (apply list)))
                                  (build)))

(defn google-auth-code-to-id-token
  "Exchange a Google authentication code, returns a Google-signed JWT ID token.
   Returns nil if the authentication code cannot be parsed.
   Each authCode can only be exchanged exactly once. Subsequent exchanges will fail."
  [^String authCode]
  (try
    (some->>
     (new GoogleAuthorizationCodeTokenRequest
          net-transport
          json-factory
          "https://oauth2.googleapis.com/token"
          (get environ/env :api-google-oauth-client-id)
          (get environ/env :api-google-oauth-client-secret)
          authCode
          (get environ/env :google-redirect-uri))
     (.execute)
     (.getIdToken))
    (catch TokenResponseException _
      (log/warn "Caught TokenResponseException during GoogleAuthorizationCodeTokenRequest construction & execution."))
    (catch Exception e
      (log/warn "Caught Exception during GoogleAuthorizationCodeTokenRequest construction & execution:" e))))

(defn parse-token
  "Parse provided Google ID token, returns a map containing the information associated with the token.
   Returns nil if the token cannot be parsed as Google Auth code nor Google ID token."
  [^String token]
   (try
     (some->> (GoogleIdToken/parse json-factory token)
              (.getPayload)
              (into {})
              (w/keywordize-keys))
     (catch IllegalArgumentException _
       nil)))

(defn verify-token-gapi
  "Return a truthy value if the token is valid."
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

(defrecord Identification [id-token token-info person])

(defn verified-person
  "Return a person from the database having a matching stored token."
  [db auth-token-conf parsed-token]
  (let [email (:email parsed-token)]
    (when-let [{stored-token :person/auth-token} (d/pull db
                                                       '[:person/auth-token]
                                                       [:person/email email])]

      (when (try
              (bsc/unsign stored-token (:key auth-token-conf)
                          (select-keys auth-token-conf [:max-age]))
              (catch Exception _
                (log/debug "Failed to unsigned stored token"
                           {:stored-token stored-token
                            :key (:key auth-token-conf)})))
        (query-person db :person/email email)))))

(defn identify
  "Identify the wormbase user associated with request based on token.
   Token can be either Google Auth code or Google ID token.
   In case Google Auth code, auth code will be exchanged for a Google ID token.
   Conditionally associates the authentication token with the user in the database.
   Return an Identification record."
  [request ^String token]
  (let [auth-token-conf (:auth-token app-conf)
        google-ID-token (or (google-auth-code-to-id-token token)
                            token)
        parsed-token (try
                       (parse-token google-ID-token)
                       (catch Exception ex
                         (log/error ex "Malformed token")))
        db (:db request)]
    (when parsed-token
      (if-let [person (verified-person db auth-token-conf parsed-token)]
        (map->Identification {:id-token google-ID-token :token-info parsed-token :person person})
        (when-let [tok (verify-token token)]
          (if-let [person (query-person db :person/email (:email tok))]
            (let [auth-token (sign-token auth-token-conf tok)]
              @(d/transact-async (:conn request)
                                 [[:db/add
                                   (:db/id person)
                                   :person/auth-token
                                   auth-token]])
              (map->Identification {:id-token google-ID-token :token-info parsed-token :person person}))
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


