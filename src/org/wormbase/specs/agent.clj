(ns org.wormbase.specs.agent
  (:require [clojure.spec.alpha :as s]))

(s/def :agent/script keyword?)

(s/def :agent/web-form keyword?)

(def all-agents #{:agent/script
                  :agent/web-form})

(s/def ::agents all-agents)

(s/def :agent/id ::agents)

