(ns wormbase.specs.common
  (:require [clojure.spec.alpha :as s]
            [spec-tools.core :as stc]
            [spec-tools.spec :as sts]))

(s/def ::info sts/map?)

(s/def ::message sts/string?)

(s/def ::error-response (stc/spec (s/keys :req-un [::info ::message])))

(s/def ::matches (stc/spec (s/coll-of ::find-match :kind vector?)))

(s/def ::find-result (stc/spec (s/keys :req-un [::matches])))

(s/def ::entity-type sts/string?)
