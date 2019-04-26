(ns wormbase.specs.agent
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]))

(s/def :agent/console sts/keyword?)

(s/def :agent/web sts/keyword?)

(def all-agents (stc/spec {:spec #{:agent/console :agent/web :agent/importer}}))

(s/def ::id all-agents)

