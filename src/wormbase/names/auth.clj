(ns wormbase.names.auth
  (:require
   [clojure.walk :as w]
   [buddy.auth :as auth]
   [buddy.auth.backends.token :as babt]
   [buddy.auth.middleware :as auth-mw]
   [wormbase.names.agent :as wn-agent]
   [wormbase.names.util :as util]
   [ring.middleware.defaults :as rmd]
   [datomic.api :as d]
   [wormbase.names.util :as wnu]
   [clojure.string :as str]);
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
      ;; TODO: logging
      (println ex)
      nil)))

(defn identify [request token]
  (when-let [tok (verify-token token)]
    (let [lur [:person/email (:email tok)]
          db (:db request)]
      (when-let [person (d/entity db lur)]
        (let [x (merge tok (wnu/entity->map person))]
          (when (str/starts-with? (:email x) "tester2")
            (println "ROLES for person in identify:"
                     (:person/roles x)))
          x)))))

(def backend (babt/token-backend {:authfn identify}))

(defn wrap-auth
  [handler]
  (-> handler
      (auth-mw/wrap-authentication backend)
      (auth-mw/wrap-authorization backend)
      (rmd/wrap-defaults rmd/api-defaults)))

(defn authenticated? [req]
  (println "------------------------ CHECKING AUTH: ---------------------")
  (println)
  (auth/authenticated? req))

;; Requires that (:identity req) is a map containing the role.
(defn admin [req]
  (and (authenticated? req)
       (#{:admin} (:role (:identity req)))))
