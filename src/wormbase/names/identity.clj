(ns wormbase.names.identity
  (:require
   [compojure.api.sweet :as sweet]
   [ring.util.http-response :refer [bad-request created not-found!
                                    not-modified ok]]
   [wormbase.specs.identity :as wsident]
   [wormbase.util :as wu]
   [wormbase.names.util :as wnu]
   [wormbase.names.auth :as wna]))

(def ^:private app-conf (wu/read-app-config))

(def ^:private gapps-conf (:google-apps app-conf))

(defn get-identity [request]
  (let [identity (wnu/unqualify-keys (-> request :identity) "identity")
        person (wnu/unqualify-keys (:person identity) "person")
        id-token (:id-token identity)
        token-info (:token-info identity)]
    (ok {:person person
         :id-token id-token
         :token-info token-info} )))


(def routes
  (sweet/routes
   (sweet/context "/identity" []
     :tags ["identity"]
     (sweet/resource
      {:get
       {:summary "Get the identity for the authenticated and authorized user making the request."
        :x-name ::get-identity
        :responses (wnu/http-responses-for-read {:schema ::wsident/identity-response})
        :handler get-identity}}))))
