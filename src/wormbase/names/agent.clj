(ns wormbase.names.agent
  (:require
   [clojure.spec.alpha :as s]
   [wormbase.specs.agent :as wsa]
   [wormbase.names.util :as wnu]))

(def gapps-conf (:google-apps (wnu/read-app-config)))

(defn identify [token]
  (let [app-conf (wnu/read-app-config)
        client-types wsa/all-agents
        client-id-map (zipmap
                       (map #(-> gapps-conf % :client-id)
                            (map (comp keyword name) client-types))
                       client-types)
        aud (:aud token)]
    (get client-id-map aud)))
