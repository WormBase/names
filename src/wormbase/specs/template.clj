(ns wormbase.specs.template
  (:require [clojure.spec.alpha :as s]))

(s/def :template/describes keyword?)

(s/def :template/format string?)
