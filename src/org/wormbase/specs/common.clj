(ns org.wormbase.specs.common
  (:require [clojure.spec.alpha :as s]
            [spec-tools.spec :as st]))

(s/def ::spec-info st/map?)

(s/def ::spec (s/keys :req-un [::spec-info]))

(s/def ::error-response (s/keys :req-un [::spec]))
