(ns wormbase.names.agent
  (:require
   [clojure.walk :as w]
   [environ.core :as environ]
   [wormbase.specs.agent :as wsa])
  (:import
   (com.google.api.client.googleapis.auth.oauth2 GoogleIdToken$Payload
                                                 GoogleIdToken)))

(defprotocol AgentIdentity
  (-identify [token]))

(extend-protocol AgentIdentity

  GoogleIdToken
  (-identify [token]
    (-identify (.getPayload token)))

  GoogleIdToken$Payload
  (-identify [token]
    (-identify (w/keywordize-keys (into {} token))))

  clojure.lang.APersistentMap
  (-identify [token]
    (let [client-types (:form wsa/all-agents)
          client-id-map (zipmap
                         (map (get environ/env :api-google-oauth-client-id)
                              (map (comp keyword name) client-types))
                         client-types)
          aud (:aud token)]
      (get client-id-map aud))))

(defn identify [token]
  (-identify token))
