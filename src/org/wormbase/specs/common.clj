(ns org.wormbase.specs.common
  (:require [clojure.spec.alpha :as s]))

(s/def ::info map?)

(s/def ::spec (s/keys :req-un [::info]))

(s/def ::error-response (s/keys :req-un [::spec]))

(s/def ::conflict-response (s/keys :req-un [::info]))
