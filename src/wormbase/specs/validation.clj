(ns wormbase.specs.validation
  (:require
   [clojure.spec.alpha :as s]
   [datomic.api :as d]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]
   [wormbase.specs.common :as wsc]))

(s/def ::name string?)

(s/def ::value any?)

(s/def ::name-error (s/keys :req-un [::name ::value]))

(s/def ::name-errors (s/coll-of ::name-error :min-count 1))

(s/def ::errors-map (s/keys :req-un [::name-errors]))

(s/def ::error-response (stc/spec {:spec (stc/merge ::errors-map ::wsc/error-response)
                                   :description "Name validation errors"}))




