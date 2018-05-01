(ns wormbase.specs.biotype
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.spec :as sts]))

(s/def :biotype/cds sts/keyword?)

(s/def :biotype/psuedogene sts/keyword?)

(s/def :biotype/transcript sts/keyword?)

(s/def :biotype/transposon sts/keyword?)

(def all-biotypes #{:biotype/cds
                    :biotype/psuedogene
                    :biotype/transcript
                    :biotype/transposon})

(s/def ::biotypes all-biotypes)

  
