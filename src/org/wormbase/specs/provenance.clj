(ns org.wormbase.specs.provenance
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(def operations #{"addName"
                  "changeName"
                  "created"
                  "delName"
                  "import"
                  "killed"
                  "mergedFrom"
                  "mergedTo"
                  "resurrected"
                  "splitFrom"
                  "splitTo"})

(def clients #{"import-script"
               "reconcilation-script"
               "rest-api"
               "web-form"})

(def instant? (partial instance? java.util.Date))

(def user? (partial re-matches #"[\w\-\.]+@wormbase.org"))

(s/def ::how (s/and string? clients))
(s/def ::when instant?)
(s/def ::why (s/and string? operations))
(s/def ::who (s/and string?))
(s/def ::audit (s/keys :req [::how ::when ::why ::who]))

