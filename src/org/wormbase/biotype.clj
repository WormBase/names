(ns org.wormbase.biotype
  (:require [clojure.spec.alpha :as s]
            [org.wormbase.specs.biotype :as owsb]
            [spec-tools.spec :as st]))

(defprotocol BioTypeIdent
  (-convert-to-ident [value]))

(extend-protocol BioTypeIdent
  String
  (-convert-to-ident [value]
    (keyword "biotype" value))

  clojure.lang.Keyword
  (-convert-to-ident [value]
    (if ((comp namespace empty?) value)
      (-convert-to-ident (name value))
      value)))

(s/fdef convert-to-ident
        :args (s/cat :biotype-as-str st/string?)
        :ret ::owsb/id)
(defn convert-to-ident [value]
  (-convert-to-ident value))
