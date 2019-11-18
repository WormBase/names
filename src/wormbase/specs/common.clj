(ns wormbase.specs.common
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]))

(s/def ::info (stc/spec {:spec sts/map?
                         :swagger/example {:problems ["clojure.spec error data."]}
                         :description "Information pertaining to an error."}))

(s/def ::message (stc/spec {:spec sts/string?
                            :swagger/example "The request could not be processed due to X."
                            :description "The error message."}))

(s/def ::error-response (stc/spec {:spec (s/keys :req-un [::message]
                                                 :opt-un [::info])
                                   :description "A mapping describing a general error response."}))

(s/def ::find-term (stc/spec {:spec (s/and string? (complement str/blank?))
                              :swagger/example "unc-2"
                              :description "A search term."}))

(s/def ::pattern ::find-term)

(s/def ::find-request (stc/spec {:spec (s/keys :req-un [::pattern])
                                 :description "A mapping containing the search term pattern."}))

(s/def ::matches (s/coll-of map?))

(s/def ::find-response (stc/spec {:spec (s/keys :req-un [::matches])
                                  :description "The results of a find request."}))

(s/def ::entity-type sts/string?)

(s/def ::force? sts/boolean?)

