(ns wormbase.names.auth
  (:require [buddy.auth :as auth]
            [buddy.auth.accessrules :as baa]
            [buddy.auth.backends.token :as babt]
            [buddy.auth.middleware :as auth-mw]
            [buddy.sign.compact :as bsc]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [clojure.walk :as w]
            [datomic.api :as d]
            [environ.core :as environ]
            [ring.middleware.defaults :as rmd]
            [ring.util.http-response :as http-response]
            [wormbase.util :as wu])
  (:import (com.google.api.client.auth.oauth2 TokenResponseException)
           (com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeTokenRequest GoogleIdToken GoogleIdTokenVerifier$Builder)
           (com.google.api.client.http.javanet NetHttpTransport)
           (com.google.api.client.json.gson GsonFactory)))

(def ^:private net-transport (NetHttpTransport.))

(def ^:private json-factory (GsonFactory.))

(def ^:private app-conf (wu/read-app-config))

(def ^:private token-verifier (.. (GoogleIdTokenVerifier$Builder. net-transport
                                                                  json-factory)
                                  (setAudience (list (get environ/env :api-google-oauth-client-id)))
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
          (get environ/env :api-google-oauth-client-id)
          (get environ/env :api-google-oauth-client-secret)
          authCode
          (get environ/env :google-redirect-uri))
     (.execute)
     (.getIdToken))
    (catch TokenResponseException _)
    (catch Exception e
      (log/warn "Caught Exception during GoogleAuthorizationCodeTokenRequest construction & execution:" e))))

(defn parse-token-str
  "Parse provided Google ID token string, returns a GoogleIdToken object.
   Returns nil if the token string cannot be parsed as Google ID token."
  ^GoogleIdToken
  [^String token-str]
  (if (not (or
            (str/blank? token-str)
            (= token-str "null")))
    (try
      (GoogleIdToken/parse json-factory token-str)
      (catch java.io.IOException e
        (log/warn "Caught invalid token-str. IOException:" e))
      (catch Exception ex
        (log/error "parse-token-str exception:" ex ". token-str provided:" token-str)))
    nil))

(defn get-id-token-payload
  "Get the GoogleIdToken payload containing the information associated with the token
   (from a GoogleIdToken object)."
  [^GoogleIdToken token]
  (try
    (.getPayload token)
    (catch IllegalArgumentException _
      (log/error "Invalid googleIdToken object provided. getPayload method not found."))))

(defn to-keywordized-map
  "Convert an object into a keywordized map"
  [object]
  (some->> object
           (into {})
           (w/keywordize-keys)))

(defn verify-token-gapi
  "Returns true if the token is valid."
  [^String token]
  (some-> (parse-token-str token)
          (.verify token-verifier)))

(defn verify-token
  "Verify the token via Google API(s).
  Returns a map containing the information held in the Google ID Token."
  [^String token]
  (if (verify-token-gapi token)
    (let [gtoken (some-> (parse-token-str token)
                         (get-id-token-payload))]
      (if (and gtoken
               (= (.getHostedDomain gtoken) "wormbase.org")
               (true? (.getEmailVerified gtoken)))
        (to-keywordized-map gtoken)
        (log/warn "Token failed domain verification (token must correspond to verified @wormbase.org email address).")))
    (log/warn "Token failed google API verification.")))

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

(defn get-verified-person
  "Returns a person from the database with:
   * Matching email address
   * Active profile state in DB
   * A matching stored (unsigned) auth-token"
  [db auth-token-conf token-str email]

  (when-let [person (d/pull db
                            '[:person/auth-token :person/active?]
                            [:person/email email])]

    (when (:person/active? person)
      (let [stored-token (:person/auth-token person)
            unsigned-stored-token (try
                                    (bsc/unsign stored-token (:key auth-token-conf)
                                                (select-keys auth-token-conf [:max-age]))
                                    (catch Exception _
                                      (log/error "Failed to unsigned stored token"
                                                 {:stored-token stored-token
                                                  :key (:key auth-token-conf)})))]
        (when (and (not (nil? unsigned-stored-token))
                   (not (nil? token-str))
                   (= unsigned-stored-token token-str))
          (query-person db :person/email email))))))

(defn identify
  "Identify the wormbase user associated with request based on token.
   Token can be either Google Auth code or Google ID token.
   In case Google Auth code, auth code will be exchanged for a Google ID token.
   Conditionally associates the authentication token with the user in the database.
   Return an Identification record."
  [request ^String token]
  (let [auth-token-conf (:auth-token app-conf)
        google-ID-token-str (or (google-auth-code-to-id-token token)
                                token)
        parsed-token-map (some-> (parse-token-str google-ID-token-str)
                                 (get-id-token-payload)
                                 (to-keywordized-map))
        email (some-> parsed-token-map
                      (:email parsed-token-map))
        db (:db request)]
    (log/debug "Initiating token-based identification.")
    (if parsed-token-map
      (if-let [person (get-verified-person db auth-token-conf google-ID-token-str email)]
        ;Verified person found matching token
        (do
          (log/debug "Verified person found:" person)
          (map->Identification {:id-token google-ID-token-str :token-info parsed-token-map :person person}))
        ;No verified person found matching token
        (if-let [tok (verify-token google-ID-token-str)]
          ;Provided token verified
          (if-let [person (query-person db :person/email (:email tok))]
            ;Person found matching verified token
            (let [signed-auth-token (sign-token auth-token-conf google-ID-token-str)
                  stored-auth-token (some-> (d/pull db
                                                    '[:person/auth-token]
                                                    [:person/email email])
                                            (:person/auth-token))]
              (log/debug "No verified person found, but token verified as valid. Person matching verified token: " person)
              (when (nil? stored-auth-token)
                (log/debug "Storing new auth-token for user " (:email tok))
                @(d/transact-async (:conn request)
                                   [[:db/add
                                     (:db/id person)
                                     :person/auth-token
                                     signed-auth-token]]))
              (map->Identification {:id-token google-ID-token-str :token-info parsed-token-map :person person}))
            ;No person found matching verified token
            (log/warn "No person found in db for verified token:" tok))
          ;Provided token fails to verify
          (log/warn "Unverified token received (and no verified person found).")))
      (log/warn "Empty token received or token failed to parse."))))

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
