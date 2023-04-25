(ns wormbase.specs.identity
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]))

(s/def ::authcode (stc/spec {:spec sts/string?
                             :swagger/example "4/P7q7W91a-oMsCeLvIaQm6bTrgtp7"
                             :description "A Google OAuth2 authorization response code"}))

(s/def ::access_token (stc/spec {:spec sts/string?
                             :swagger/example "..."
                             :description "A Google API access token"}))

(s/def ::id_token (stc/spec {:spec sts/string?
                             :swagger/example "..."
                             :description "A Google-signed JWT containing identity information about the user"}))

(s/def ::expires_in (stc/spec {:spec sts/integer?
                             :swagger/example "30"
                             :description "The remaining lifetime of the access token in seconds"}))

(s/def ::scope (stc/spec {:spec (s/coll-of sts/string? )
                             :swagger/example "[]"
                             :description "A collection of (Google) scopes the access token grants access to."}))

(s/def ::token_type (stc/spec {:spec sts/string?
                             :swagger/example "Bearer"
                             :description "The type of access token returned."}))

(s/def ::refresh_token (stc/spec {:spec sts/string?
                             :swagger/example "4/P7q7W91a-oMsCeLvIaQm6bTrgtp7"
                             :description "A Google OAuth2 authorization response code"}))

(def example-id-token-response {:id_token "..."})

(s/def ::id-token-response (stc/spec {:spec (s/keys :req-un [::id_token])
                                      :swagger/example example-id-token-response}))
