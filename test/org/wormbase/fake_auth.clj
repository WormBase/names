(ns org.wormbase.fake-auth
  (:require [org.wormbase.names.auth :as own-auth]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]))

(def tokens {"tester@wormbase.org" "TOKEN_HERE_tester1"
             "tester2@wormbase.org" "TOKEN_HERE_tester2"
             "tester3@wormbase.org" "TOKEN_HERE_tester3"})

(def ^:dynamic *current-user* nil)

(defn token
  ([identity]
   (get tokens identity))
  ([]
   (token *current-user*)))

(alter-var-root
 (var own-auth/who-am-i)
 (fn fake-google-people-me-api [real-fn]
   (fn [url]
     (log/trace "NOTICE: Faking requests to Google APIs")
     {:body (json/generate-string
             {:resourceName "people/123213123123"
              :etag "madeupandtotallybogus"
              :source {:type "PROFILE"
                       :id -1}
              :names [{:metadata {:primary true}
                       :displayName "Tester"
                       :familyName "McTester "
                       :givenName "Tester could be 1 2 or 3"
                       :displayNameFirstLAst "McTester, Tester"}]
              :emailAddresses
              [{:metadata {:primary true
                           :verified true
                           :source {:type "DOMAIN_PROFILE"
                                    :id -2}}
                :value *current-user*}]})})))
