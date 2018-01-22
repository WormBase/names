(ns org.wormbase.names.user
  (:require
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [org.wormbase.names.auth.restructure] ;; TBD: side effects?   
   [org.wormbase.specs.user :as ows-user]
   [spec-tools.spec :as st]
   [ring.util.http-response :as http-response]
   [clj-http.client :as http]))

(defn create [request]
  (let [conn (:conn request)
        user-recs (some-> request :body-params :users)
        tx-res (d/transact
                conn
                [[:wb.dbfns/new-users user-recs ::ows-user/new]])]
    (if tx-res
      (http-response/created
       {:message (format "Created %d users" (count user-recs))})
      (http-response/bad-request))))

(def routes
  (sweet/context "/user/" []
    :tags ["user"]
    (sweet/resource
     {:coercion :spec
      :post
      {:summary "Create new users."
       :x-name ::new
       :parameters {:body-params {:user ::ows-user/new}}
       :responses {201 {:schema {:created ::ows-user/created}}}
       :roles #{:admin}
       :handler (fn [request]
                  (http-response/bad-request "TBD")
                  ;; (create request)
                  )
       }})))

