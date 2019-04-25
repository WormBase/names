(ns wormbase.specs.recent
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.spec :as sts]
   [spec-tools.core :as stc]
   [wormbase.specs.agent :as wsa]
   [wormbase.specs.provenance :as wsp]))

(s/def ::from (s/nilable inst?))

(s/def ::until (s/nilable inst?))

(s/def ::activities (stc/spec {:spec (s/coll-of ::wsp/temporal-change)}))

(s/def ::how (stc/spec {:spec (s/and sts/keyword? #{:agent/console :agent/web})
                        :into #{}
                        :type :keyword}))
