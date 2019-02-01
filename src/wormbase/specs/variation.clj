(ns wormbase.specs.variation
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.spec :as sts]
   [spec-tools.core :as stc]))

(def id-regexp #"WBVar\d{8}")

(def name-regexp #"\w+\d+")

(s/def :variation/id (stc/spec {:spec (s/and string? (partial re-matches id-regexp))}))

(s/def :variation/name (stc/spec {:spec (s/and string? (partial re-matches name-regexp))}))

(s/def :variation/status sts/keyword?)


