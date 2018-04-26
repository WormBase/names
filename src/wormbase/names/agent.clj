(ns wormbase.names.agent
  (:require
   [clojure.spec.alpha :as s]
   [wormbase.specs.agent :as owsa]
   [wormbase.names.util :as ownu]))

(def gapps-conf (:google-apps (ownu/read-app-config)))

(defn identify [token]
  (let [app-conf (ownu/read-app-config)
        client-types owsa/all-agents
        client-id-map (zipmap
                       (map #(-> gapps-conf % :client-id)
                            (map (comp keyword name) client-types))
                       client-types)
        aud (:aud token)]
    (get client-id-map aud)))
