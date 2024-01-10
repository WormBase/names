(ns wormbase.specs.auth
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]
   [wormbase.specs.person :as wsp]))

(s/def ::person (stc/spec {:spec ::wsp/summary
                                 :swagger/example ::wsp/example-summary
                                 :description "A WB names person summary"}))

(s/def ::authcode (stc/spec {:spec sts/string?
                             :swagger/example "4/P7q7W91a-oMsCeLvIaQm6bTrgtp7"
                             :description "A Google OAuth2 authorization response code"}))

(s/def ::id-token (stc/spec {:spec sts/string?
                             :swagger/example "..."
                             :description "A Google-signed JWT containing identity information about the user"}))

(def example-token-info {:given_name "Some"
                         :family_name "User"
                         :name "Some User"
                         :email "some.user@wormbase.org"
                         :email_verified true
                         :hd "wormbase.org"
                         :... "..."})

(def example-identity {:id-token "..."
                       :token-info example-token-info
                       :person wsp/example-summary})

(s/def ::token-info (stc/spec {:spec sts/any?
                               :swagger/example example-token-info
                               :description "Parsed JWT token info."}))

(s/def ::identity-response (stc/spec {:spec (s/keys :req-un [::id-token ::token-info ::person])
                                      :swagger/example example-identity}))

(s/def ::token-stored? (stc/spec {:spec sts/boolean?
                                  :swagger/example false
                                  :description "Boolean indicating whether or not a token is currently stored for a person."}))

(s/def ::last-used (stc/spec {:spec (s/nilable sts/inst?)
                              :swagger/example "2023-12-19T14:11:34Z"
                              :description "Datestring indicating when a token was last used to access the name service."}))

(def example-token-metadata {:token-stored? true
                             :last-used "2023-12-19T14:11:34Z"})

(s/def ::token-metadata-response (stc/spec {:spec (s/keys :req-un [::token-stored? ::last-used])
                                            :swagger/example example-token-metadata}))

(s/def ::empty-response (stc/spec {:spec (s/cat)}))
