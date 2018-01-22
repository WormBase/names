(ns org.wormbase.specs.template
  (:require [clojure.spec.alpha :as s]))

(s/def :template/describes keyword?)

(s/def :template/format string?)

(def db-specs [[:template/describes {:db/unique :db.unique/value}]
               [:template/format {:db/unique :db.unique/value}]])
