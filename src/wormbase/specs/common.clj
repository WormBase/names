(ns wormbase.specs.common
  (:require [clojure.spec.alpha :as s]
            [spec-tools.core :as stc]
            [spec-tools.spec :as sts]))

(s/def ::info sts/map?)

(s/def ::spec (stc/spec (s/keys :req-un [::info])))

(s/def ::error-response (stc/spec (s/keys :req-un [::spec])))

(s/def ::conflict-response (stc/spec (s/keys :req-un [::info])))
