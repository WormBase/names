(ns org.wormbase.api-test-client
  (:require
   [clojure.test :as t]
   [org.wormbase.fake-auth :as fake-auth]
   [org.wormbase.test-utils :as tu]
   [org.wormbase.names.service :as service]))

(def default-user "tester@wormbase.org")

(defn new
  [entity-kind data & {:keys [current-user]
                       :or {current-user default-user}}]
  (binding [fake-auth/*gapi-verify-token-response*
            {"email" current-user}]
    (let [data (pr-str data)
          token (get fake-auth/tokens current-user)
          [status body]
          (tu/raw-put-or-post*
           service/app
           (str "/" entity-kind "/")
           :post
           data
           "application/edn"
           {"authorization" (str "Token " token)})]
      [status (tu/parse-body body)])))

(defn update
  [entity-kind identifier data & {:keys [current-user]
                                  :or {current-user default-user}}]
  (let [uri (str "/" entity-kind "/" identifier)
        put (partial tu/raw-put-or-post* service/app uri :put)
        token (get fake-auth/tokens current-user)
        headers {"authorization" (str "Token " token)}
        [status body] (put (pr-str data) nil headers)]
    [status (tu/parse-body body)]))

(defn info
  [entity-kind identifier & {:keys [current-user params]
                             :or {current-user "tester@wormbase.org"
                                  params {}}}]
  (binding [fake-auth/*gapi-verify-token-response* {"email" current-user}]
    (let [current-user-token (get fake-auth/tokens current-user)
          headers {"content-type" "application/edn"
                   "authorization" (str "Token " current-user-token)}
          [status body] (tu/get*
                         service/app
                         (str "/" entity-kind "/" identifier)
                         params
                         headers)]
      [status (tu/parse-body body)])))

