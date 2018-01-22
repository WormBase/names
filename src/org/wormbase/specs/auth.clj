(ns org.wormbase.specs.auth
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [spec-tools.spec :as sts]))

;;; TODO: perhaps use urly instead of re-inventing wheels?
;;;   https://github.com/michaelklishin/urly


(def https? #(str/starts-with? % "https://"))

(def absolute-uri (s/and sts/string? https?))

(def relative-uri (or absolute-uri (s/and sts/string? #(str/starts-with? % "/"))))

(s/def ::authorize-uri absolute-uri)

(s/def ::access-token-uri (s/and sts/string? https?))

(s/def ::redirect-uri relative-uri)

(s/def ::client-secret (s/and sts/string? not-empty))

(s/def ::client-id (s/spec (s/and sts/string? not-empty)))

(s/def ::auth-profile (s/keys :req-un [::client-secret ::client-id]))

(s/def ::outh2-profile (s/keys :req-un [::authorize-uri
                                        ::access-token-uri
                                        ::redirect-uri
                                        ::launch-uri
                                        ::landing-uri
                                        ::scopes
                                        ::client-id
                                        ::client-secret]))

(s/def ::authorization (s/and sts/string? #(str/starts-with? "Bearer " %)))

(s/def ::bearer-authentication ::authorization)

(s/def ::headers (s/keys :req-un [::auth-header]))
