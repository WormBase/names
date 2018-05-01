(ns wormbase.api-test-client
  (:require
   [clojure.string :as str]
   [clojure.test :as t]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.names.service :as service]
   [wormbase.test-utils :as tu])
  (:refer-clojure :exclude [update]))

(def default-user "tester@wormbase.org")

(defn new
  [entity-kind data & {:keys [current-user]
                       :or {current-user default-user}}]
  (binding [fake-auth/*gapi-verify-token-response* {"email" current-user}]
    (let [data (tu/->json data)
          token (get fake-auth/tokens current-user)
          [status body]
          (tu/raw-put-or-post*
           service/app
           (str "/" entity-kind "/")
           :post
           data
           "application/json"
           {"authorization" (str "Token " token)})]
      [status (tu/parse-body body)])))

(defn update
  [entity-kind identifier data & {:keys [current-user]
                                  :or {current-user default-user}}]
  (binding [fake-auth/*gapi-verify-token-response* {"email" current-user}]
    (let [uri (str "/" entity-kind "/" identifier)
          put (partial tu/raw-put-or-post* service/app uri :put)
          token (get fake-auth/tokens current-user)
          headers {"authorization" (str "Token " token)}
          [status body] (put (tu/->json data) "application/json" headers)]
      [status (tu/parse-body body)])))

(defn info
  [entity-kind identifier & {:keys [current-user params]
                             :or {current-user "tester@wormbase.org"
                                  params {}}}]
  (let [current-user-token (get fake-auth/tokens current-user)
        headers {"content-type" "application/edn"
                 "authorization" (str "Token " current-user-token)}
        [status body] (tu/get*
                       service/app
                       (str "/" entity-kind "/" identifier)
                       params
                       headers)]
    [status (tu/parse-body body)]))

(defn delete
  [entity-kind path & {:keys [current-user]
                       :or {current-user default-user}}]
  (binding [fake-auth/*gapi-verify-token-response* {"email" current-user}]
    (let [current-user-token (get fake-auth/tokens current-user)
          uri (str "/" entity-kind "/" (str/replace-first path #"^/" ""))]
      (tu/delete service/app
                 uri
                 "application/edn"
                 {"authorization" (str "Token " current-user-token)}))))
