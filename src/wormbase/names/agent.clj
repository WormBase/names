(ns wormbase.names.agent
  (:require
   [clojure.spec.alpha :as s]
   [wormbase.specs.agent :as wsa]
   [wormbase.names.util :as wnu])
  (:import
   (com.google.api.client.googleapis.auth.oauth2 GoogleIdToken$Payload)))

(def gapps-conf (:google-apps (wnu/read-app-config)))

(defn identify [^GoogleIdToken$Payload token]
  (let [client-types (:form wsa/all-agents)
        client-id-map (zipmap
                       (map #(-> gapps-conf % :client-id)
                            (map (comp keyword name) client-types))
                       client-types)
        aud (.getAudience token)]
    (get client-id-map aud)))
