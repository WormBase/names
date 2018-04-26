(ns wormbase.fake-auth
  (:require
   ;; TODO: loggigng
   ;; [clojure.tools.logging :as log]
   [cheshire.core :as json]
   [environ.core :as environ]
   [wormbase.names.auth :as own-auth]))

(def console-client-id (own-auth/client-id :console))

(def web-client-id (own-auth/client-id :web))

(def tokens {"tester@wormbase.org" "TOKEN_HERE_tester1"
             "tester2@wormbase.org" "TOKEN_HERE_tester2"
             "tester3@wormbase.org" "TOKEN_HERE_tester3"})

(def defaults
  {"iat" 1234567890
   "aud" web-client-id
   "azp" web-client-id
   "sub" "0101010101010101010101"
   "hd" "wormbase.org"
   "iss" "accounts.google.com"
   "exp" 1231312
   "at_hash" "Xx0_0xx0_0xXx0_0xx0_0xX"
   "email_verified" true
   "email" "tester@wormbase.org"})

(def ^:dynamic *gapi-verify-token-response* nil)

(alter-var-root
 (var own-auth/verify-token-gapi)
 (fn fake-google-api-verify-token [token]
   (fn verify-token-pretend [token]
     ;; TODO: use logging
     (println "NOTICE: Faking verifying token with Google API")
     (merge defaults *gapi-verify-token-response*))))

