(ns wormbase.specs.biotype
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]))

(s/def ::identifier (stc/spec ;; (s/and
                     {:spec string?}
                                     ;; (fn lower-case? [bts]
                                     ;;   (every? #(Character/isLowerCase %) (name bts)))))
                               ;; )
  ))


  
