(ns org.wormbase.names.common
  (:require [clojure.spec.alpha :as s]))

(s/def ::error-response (s/keys :req-un [s/spec?]))
