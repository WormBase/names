(ns wormbase.fake-auth
  (:require
   [clojure.tools.logging :as log]
   [clojure.walk :as w]
   [environ.core :as environ]
   [wormbase.names.auth :as wn-auth])
  (:import
   (com.google.api.client.googleapis.auth.oauth2 GoogleIdToken$Payload)))

(def oauth2-client-id (get environ/env :api-google-oauth-client-id))

(def tokens {"tester@wormbase.org" "TOKEN_HERE_tester1"
             "tester2@wormbase.org" "TOKEN_HERE_tester2"
             "tester3@wormbase.org" "TOKEN_HERE_tester3"})

(def defaults
  {"iat" 1234567890
   "aud" oauth2-client-id
   "azp" oauth2-client-id
   "sub" "0101010101010101010101"
   "hd" "wormbase.org"
   "iss" "accounts.google.com"
   "exp" 1231312
   "at_hash" "Xx0_0xx0_0xXx0_0xx0_0xX"
   "email_verified" true
   "email" "tester@wormbase.org"})

(def ^:dynamic *gapi-verify-token-response* nil)

(defn payload [mapping]
  (let [pl (GoogleIdToken$Payload.)]
    (doseq [[k v] mapping]
      (.set pl k v))
    pl))

(alter-var-root
 (var wn-auth/verify-token)
 (fn fake-google-api-verify-token [_]
   (fn [_]
     (log/debug "NOTICE: Faking wn-auth/verify-token (verifying token with Google API)")
     (when-not (nil? *gapi-verify-token-response*)
       (doseq [[k v] defaults]
         (when-not (get *gapi-verify-token-response* k)
           (.set *gapi-verify-token-response* k v)))
       (some->> *gapi-verify-token-response*
                (into {})
                (w/keywordize-keys))))))

(alter-var-root
 (var wn-auth/get-token-payload-map)
 (fn fake-get-token-payload-map [_]
   (fn [_]
     (log/debug "NOTICE: faking wna-auth/get-token-payload-map")
     (merge
      (w/keywordize-keys defaults)
      (some->> *gapi-verify-token-response*
               (into {})
               (w/keywordize-keys))))))

