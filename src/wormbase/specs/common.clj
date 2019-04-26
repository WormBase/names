(ns wormbase.specs.common
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]))

(s/def ::info (stc/spec {:spec sts/map?
                         :swagger/example {:problems ["A sequence of clojure.spec error data."]}
                         :description "Information pertaining to an error."}))

(s/def ::message (stc/spec {:spec sts/string?
                            :swagger/example "The event could not be process due to X."
                            :description "The error message."}))

(s/def ::error-response (stc/spec {:spec (s/keys :req-un [::info ::message])
                                   :description "A mapping describing an error response."}))

(s/def ::find-term (stc/spec {:spec (s/and string? (complement str/blank?))
                              :swagger/example "unc-2"
                              :description "A search term."}))

(s/def ::pattern ::find-term)

(s/def ::find-request (stc/spec {:spec (s/keys :req-un [::pattern])
                                 :description "A mapping containing the search term pattern."}))

(s/def ::entity-type sts/string?)
