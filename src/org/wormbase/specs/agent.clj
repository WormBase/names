(ns org.wormbase.specs.agent
  (:require
   [clojure.spec.alpha :as s]))

(s/def :agent/console keyword?)

(s/def :agent/web keyword?)

(def all-agents #{:agent/console :agent/web})

(s/def ::agent all-agents)

(s/def :agent/id ::agent)
