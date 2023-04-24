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

(defn get-identity-id-token [request]
  (-> request :identity :id-token))


(def routes
  (sweet/routes
   (sweet/context "/identity/id-token" []
     :tags ["identity"]
     (sweet/resource
      {:get
       {:summary "Get the ID token for the authenticated and authorized user making the request."
        :x-name ::get-id-token
        :responses (wnu/http-responses-for-read {:schema ::wsident/id_token})
        :handler get-identity-id-token}}))))
