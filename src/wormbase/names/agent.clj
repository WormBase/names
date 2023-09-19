(ns wormbase.names.agent
  (:require [user-agent :as ua]
            [wormbase.specs.agent :as wsa])
  )

(defn identify [request-header]
  (let [AGENT-MAP {:BROWSER :agent/web :LIBRARY :agent/console :UNKNOWN :agent/console}
        user-agent (get request-header "user-agent")]
    (if (nil? user-agent)
      :agent/console  ;; Assume console agent if user-agent is not set in HTTP header
      (-> (ua/parse user-agent)
          (get :type)
          (#(get AGENT-MAP %))))))
