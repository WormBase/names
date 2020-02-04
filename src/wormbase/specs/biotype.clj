(ns wormbase.specs.biotype
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]))

(s/def ::identifier (stc/spec {:spec (s/and string? not-empty)}))

  
