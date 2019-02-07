(ns wormbase.specs.variation
  (:require
   [clojure.spec.alpha :as s]))

(def id-regexp #"WBVar\d{8}")

(def name-regexp #"(([a-z]+)(Df|Dp|Ti|T|Is)?([1-9]+))*")

(s/def :variation/id (s/and string? (partial re-matches id-regexp)))

(s/def :variation/name (s/and string? (partial re-matches name-regexp)))

(s/def :variation/status keyword?)
