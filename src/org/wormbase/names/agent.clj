(ns org.wormbase.names.agent
  (:require
   [clojure.spec.alpha :as s]
   [org.wormbase.names.util :as ownu]))

(s/def ::console keyword?)

(s/def ::web keyword?)

(def all-agents #{::console ::web})

(s/def ::agent all-agents)

(s/def :agent/id ::agent)

(def gapps-conf (:google-apps (ownu/read-app-config)))

(defn identify [token]
  (let [app-conf (ownu/read-app-config)
        client-types [::web ::console]
        client-id-map (zipmap
                       (map #(-> gapps-conf % :client-id)
                            (map (comp keyword name) client-types))
                       client-types)
        aud (:aud token)]
    (get client-id-map aud)))
