(ns wormbase.api-test-client
  (:require
   [clojure.string :as str]
   [clojure.test :as t]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.names.service :as service]
   [wormbase.test-utils :as tu])
  (:refer-clojure :exclude [update]))

(def default-user "tester@wormbase.org")

(defn make-auth-payload
  [& {:keys [current-user] :or {current-user default-user}}]
  (fake-auth/payload {"email" current-user}))

(defn send-request
  [entity-kind method data & {:keys [current-user sub-path]
                              :or {current-user default-user
                                   sub-path ""}}]
    (binding [fake-auth/*gapi-verify-token-response* (make-auth-payload
                                                      :current-user
                                                      current-user)]
      (let [data (tu/->json data)
            path (str "/api/" entity-kind "/" sub-path)
            [status body] (tu/raw-put-or-post*
                           service/app
                           path
                           method
                           data
                           "application/json"
                           {"authorization" "Token FAKED"})]
        [status (tu/parse-body body)])))

(defn new
  [entity-kind data & {:keys [current-user]
                       :or {current-user default-user}}]
  (send-request entity-kind :post data :current-user current-user))

(defn update
  [entity-kind identifier data & {:keys [current-user]
                                  :or {current-user default-user}}]
  (send-request entity-kind :put data :sub-path identifier :current-user current-user))

(defn summary
  [entity-kind identifier & {:keys [current-user params]
                             :or {current-user "tester@wormbase.org"
                                  params {}}}]
  (let [headers {"content-type" "application/json"
                 "authorization" "Token FAKED"}
        path (str "/api/" entity-kind "/" identifier)
        [status body] (tu/get*
                       service/app
                       path
                       params
                       headers)]
    [status (tu/parse-body body)]))

(defn delete
  [entity-kind path & {:keys [payload current-user]
                       :or {current-user default-user
                            payload {:prov {}}}}]
  (binding [fake-auth/*gapi-verify-token-response* (make-auth-payload
                                                    :current-user
                                                    current-user)]
    (let [uri (str "/api/" entity-kind "/" (str/replace-first path #"^/" ""))]
      (tu/delete service/app
                 uri
                 "application/json"
                 (tu/->json payload)
                 {"authorization" "Token FAKED"}))))
