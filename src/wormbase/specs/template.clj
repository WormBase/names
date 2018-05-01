(ns wormbase.specs.template
  (:require [clojure.spec.alpha :as s]
            [spec-tools.spec :as sts]))

(s/def :template/describes sts/keyword?)

(s/def :template/format sts/string?)
