(ns wormbase.api-test-client
  (:require
   [clojure.string :as str]
   [wormbase.fake-auth :as fake-auth]
   [wormbase.names.service :as service]
   [wormbase.test-utils :as tu])
  (:refer-clojure :exclude [update]))

(def default-user "tester@wormbase.org")

(defn make-auth-payload
  [& {:keys [current-user] :or {current-user default-user}}]
  (fake-auth/payload {"email" current-user}))

(defn parsed-response [[status body response-headers]]
  {:status status
   :body (tu/parse-body body)
   :headers response-headers})

(defn send-request
  [entity-kind method data & {:keys [current-user sub-path uri]
                              :or {current-user default-user sub-path ""}}]
  (binding [fake-auth/*gapi-verify-token-response* (make-auth-payload
                                                    :current-user
                                                    current-user)]
    (let [data (tu/->json data)
          path (if uri
                 uri
                 (str "/api/"
                      entity-kind
                      (when-not (str/blank? sub-path)
                        "/")
                      sub-path))
          response (tu/raw-put-or-post*
                    service/app
                    path
                    method
                    data
                    "application/json"
                    {"authorization" "Token FAKED"})]
      (parsed-response response))))

(defn new
  [entity-kind data & {:keys [current-user]
                       :or {current-user default-user}}]
  (send-request entity-kind :post data :current-user current-user))

(defn update
  [entity-kind identifier data & {:keys [current-user]
                                  :or {current-user default-user}}]
  (send-request entity-kind :put data :sub-path identifier :current-user current-user))

(defn summary
  [entity-kind identifier & {:keys [params extra-headers]
                             :or {params {}
                                  extra-headers nil}}]
  (let [headers (merge {"content-type" "application/json"
                        "authorization" "Token FAKED"}
                       extra-headers)
        path (str "/api/" entity-kind (and identifier (str "/" identifier)))]
    (parsed-response
     (tu/get*
      service/app
      path
      params
      headers))))

(defn delete
  [entity-kind path & {:keys [payload current-user]
                       :or {current-user default-user
                            payload {:prov {}}}}]
  (binding [fake-auth/*gapi-verify-token-response* (make-auth-payload
                                                    :current-user
                                                    current-user)]
    (let [uri (str "/api/" entity-kind "/" (str/replace-first path #"^/" ""))]
      (parsed-response
       (tu/delete service/app
                  uri
                  "application/json"
                  (tu/->json payload)
                  {"authorization" "Token FAKED"})))))
