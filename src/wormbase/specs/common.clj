(ns wormbase.specs.common
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]))

(s/def ::info sts/map?)

(s/def ::message sts/string?)

(s/def ::error-response (stc/spec (s/keys :req-un [::info ::message])))

(s/def ::find-match (stc/spec (s/keys :req [:gene/id]
                                      :opt [:gene/cgc-name
                                            :gene/sequence-name])))

(s/def ::matches (stc/spec (s/coll-of ::find-match :kind vector?)))

(s/def ::find-term (stc/spec (s/and string? (complement str/blank?))))

(s/def ::pattern ::find-term)

(s/def ::find-request (stc/spec (s/keys :req-un [::pattern])))

(s/def ::find-result (stc/spec (s/keys :req-un [::matches])))

(s/def ::entity-type sts/string?)
