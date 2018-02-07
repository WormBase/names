(ns org.wormbase.specs.biotype
  (:require [clojure.spec.alpha :as s]))

(s/def :biotype/cds keyword?)

(s/def :biotype/psuedogene keyword?)

(s/def :biotype/transcript keyword?)

(s/def :biotype/transposon keyword?)

(def all-biotypes #{:biotype/cds
                    :biotype/psuedogene
                    :biotype/transcript
                    :biotype/transposon})

(s/def ::biotypes all-biotypes)

(s/def :biotype/id ::biotypes)

(def db-specs (concat [[:biotype/id {:db/unique :db.unique/value}]]
                      (-> all-biotypes
                          (zipmap (repeat {:db/unique :db.unique/value}))
                          vec)))
  
