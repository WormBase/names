(ns wormbase.names.auth
  (:require [buddy.auth :as auth]
            [buddy.auth.accessrules :as baa]
            [buddy.auth.backends.token :as babt]
            [buddy.auth.middleware :as auth-mw]
            [buddy.hashers :as bhasher]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [clojure.walk :as w]
            [compojure.api.sweet :as sweet]
            [datomic.api :as d]
            [environ.core :as environ]
            [ring.middleware.defaults :as rmd]
            [ring.util.http-response :as http-response]
            [wormbase.specs.auth :as ws-auth]
            [wormbase.names.util :as wnu]
            [wormbase.util :as wu])
  (:import (com.google.api.client.auth.oauth2 TokenResponseException)
           (com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeTokenRequest GoogleIdToken GoogleIdTokenVerifier$Builder)
           (com.google.api.client.http.javanet NetHttpTransport)
           (com.google.api.client.json.gson GsonFactory)))

(def ^:private net-transport (NetHttpTransport.))

(def ^:private json-factory (GsonFactory.))

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

(defn get-token-payload-map
  "Parse a Google ID token string, return a keywordized map of the GoogleIdToken.payload"
  [^String google-ID-token-str]
  (some-> (parse-token-str google-ID-token-str)
          (get-id-token-payload)
          (to-keywordized-map)))

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

(defn derive-token-hash
  "Return a hash derived from provided token for database storage."
  [token]
  (bhasher/derive (str token) {:alg :argon2id}))

(defn matching-token-hash?
  "Verify if a provided token matches a hashed token.
   Returns true if token matches, false if not, nil on error."
  [raw-token hashed-token]
  (if (or
       (str/blank? raw-token)
       (= raw-token "null")
       (str/blank? hashed-token)
       (= hashed-token "null"))
    false
    (some->
     (try
       (bhasher/verify (str raw-token) (str hashed-token))
       (catch clojure.lang.ExceptionInfo e
         (log/error "Failed to verify token with stored hash." e)))
     (:valid))))

(defrecord Identification [id-token token-info person])

(defn get-person-matching-token
  "Returns a person from the database with:
   * Matching email address
   * Active profile state in DB
   * A matching stored (signed) auth-token"
  [conn db token-str email]

  (when-let [person (d/pull db
                            '[:person/auth-token :person/active?]
                            [:person/email email])]
    (when (and (:person/active? person)
               (matching-token-hash? token-str (:person/auth-token person)))
      ;;Update :person/auth-token-last-used attribute to indicate API token usage
      (let [person (query-person db :person/email email)]
        @(d/transact conn
                     [[:db/add
                       (:db/id person)
                       :person/auth-token-last-used
                       (wu/now)]])
        person))))

(defn successful-login
  "Store the identification of a successful login
   and store the login timestamp in the DB."
  [conn identification]
  (let [person (:person identification)]
    @(d/transact conn
                 [[:db/add
                   (:db/id person)
                   :person/last-activity
                   (wu/now)]]))
  (map->Identification identification))

(defn identify
  "Identify the wormbase user associated with request based on token.
   Token can be either Google Auth code or Google ID token.
   In case Google Auth code, auth code will be exchanged for a Google ID token.
   Conditionally associates the authentication token with the user in the database.
   Return an Identification record."
  [request ^String token]
  (let [google-ID-token-str (or (google-auth-code-to-id-token token)
                                token)
        parsed-token-map (get-token-payload-map google-ID-token-str)
        email (some-> parsed-token-map
                      (:email parsed-token-map))
        db (:db request)
        conn (:conn request)]
    (log/debug "Initiating token-based identification.")
    (if parsed-token-map
      (if-let [person (get-person-matching-token conn db google-ID-token-str email)]
        ;Verified person found matching token
        (do
          (log/debug "Verified person found with matching stored auth-code:" person)
          (successful-login conn {:id-token google-ID-token-str :token-info parsed-token-map :person person}))
        ;No verified person found matching token
        (if-let [tok (verify-token google-ID-token-str)]
          ;Provided token verified
          (if-let [person (query-person db :person/email (:email tok))]
            ;Person found matching verified token
            (do
              (log/debug "No verified person found based on stored auth-code,
                          but token verified as valid. Person matching verified token: " person)
              (successful-login conn {:id-token google-ID-token-str :token-info parsed-token-map :person person}))
            ;No person found matching verified token
            (log/warn "No person found in db for verified token:" tok))
          ;Provided token fails to verify
          (log/warn "Unverified (or expired) token received (and token did not match stored auth-code of any verified person).")))
      (when (not (or (str/blank? google-ID-token-str)
                     (= google-ID-token-str "null")))
        (log/warn "Received token failed to parse." )))))

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

;; Endpoint handlers
(defn get-identity [request]
  (let [identity (wnu/unqualify-keys (-> request :identity) "identity")
        person (wnu/unqualify-keys (:person identity) "person")
        id-token (:id-token identity)
        token-info (:token-info identity)]
    (http-response/ok {:person person
                       :id-token id-token
                       :token-info token-info})))

(defn store-auth-token [request]
  (if-let [signed-auth-token (some->
                              (wnu/unqualify-keys (-> request :identity) "identity")
                              (:id-token identity)
                              (derive-token-hash))]
    (let [person (-> (wnu/unqualify-keys (-> request :identity) "identity")
                     (:person identity))]
      (log/debug "Storing new auth-token for user" (:person/email person))
      @(d/transact (:conn request)
                   [[:db/add
                     (:db/id person)
                     :person/auth-token
                     signed-auth-token]
                    [:db/add
                     (:db/id person)
                     :person/auth-token-stored-at
                     (wu/now)]])
      (http-response/ok))
    (http-response/internal-server-error)))

(defn delete-auth-token [request]
  (let [person (-> (wnu/unqualify-keys (-> request :identity) "identity")
                   (:person identity))]
    (log/debug "Revoking stored auth-token for user" (:person/email person))
    @(d/transact (:conn request)
                 [[:db/retract
                   (:db/id person)
                   :person/auth-token]
                  [:db/retract
                   (:db/id person)
                   :person/auth-token-stored-at]
                  [:db/retract
                   (:db/id person)
                   :person/auth-token-last-used]])
    (http-response/ok)))

;; API endpoints
(def routes
  (sweet/routes
   (sweet/context "/auth" []
     :tags ["authentication"]
     (sweet/context "/identity" []
       :tags ["authentication" "identity"]
       (sweet/resource
        {:get
         {:summary "Get the identity for the authenticated and authorized user making the request."
          :x-name ::get-identity
          :responses (wnu/http-responses-for-read {:schema ::ws-auth/identity-response})
          :handler get-identity}}))
     (sweet/context "/token" []
       :tags ["authenticate"]
       (sweet/resource
        {:post
         {:summary "Store the current ID token for future scripting usage. This will invalidate the previously stored token."
          :responses (wnu/response-map http-response/ok {:schema ::ws-auth/empty-response})
          :handler store-auth-token}
         :delete
         {:summary "Delete the stored token, invalidating it for future use."
          :responses (wnu/response-map http-response/ok {:schema ::ws-auth/empty-response})
          :handler delete-auth-token}}))
     )))
