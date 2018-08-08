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

(defn new
  [entity-kind data & {:keys [current-user]
                       :or {current-user default-user}}]
  (binding [fake-auth/*gapi-verify-token-response* (make-auth-payload
                                                    :current-user
                                                    current-user)]
    (let [data (tu/->json data)
          [status body]
          (tu/raw-put-or-post*
           service/app
           (str "/api/" entity-kind "/")
           :post
           data
           "application/json"
           {"authorization" "Token FAKED"})]
      [status (tu/parse-body body)])))

(defn update
  [entity-kind identifier data & {:keys [current-user]
                                  :or {current-user default-user}}]
  (binding [fake-auth/*gapi-verify-token-response* (make-auth-payload
                                                    :current-user
                                                    current-user)]
    (let [uri (str "/api/" entity-kind "/" identifier)
          put (partial tu/raw-put-or-post* service/app uri :put)
          headers {"authorization" "Token FAKED"}
          [status body] (put (tu/->json data) "application/json" headers)]
      [status (tu/parse-body body)])))

(defn info
  [entity-kind identifier & {:keys [current-user params]
                             :or {current-user "tester@wormbase.org"
                                  params {}}}]
  (let [headers {"content-type" "application/json"
                 "authorization" "Token FAKED"}
        [status body] (tu/get*
                       service/app
                       (str "/api/" entity-kind "/" identifier)
                       params
                       headers)]
    [status (tu/parse-body body)]))

(defn delete
  [entity-kind path & {:keys [current-user]
                       :or {current-user default-user}}]
  (binding [fake-auth/*gapi-verify-token-response* (make-auth-payload
                                                    :current-user
                                                    current-user)]
    (let [uri (str "/api/" entity-kind "/" (str/replace-first path #"^/" ""))]
      (tu/delete service/app
                 uri
                 "application/json"
                 {"authorization" "Token FAKED"}))))
