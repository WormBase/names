(ns org.wormbase.names.person
  (:require
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [org.wormbase.names.auth.restructure] ;; TBD: side effects?   
   [org.wormbase.specs.person :as ows-person]
   [ring.util.http-response :as http-response]))

(defn create [request]
  (let [conn (:conn request)
        person-recs (some-> request :body-params :persons)
        tx-res (d/transact
                conn
                [[:wb.dbfns/new-persons person-recs ::ows-person/new]])]
    (if tx-res
      (http-response/created
       {:message (format "Created %d persons" (count person-recs))})
      (http-response/bad-request))))

(def routes
  (sweet/context "/person/" []
    :tags ["person"]
    (sweet/resource
     {:coercion :spec
      :post
      {:summary "Create new persons."
       :x-name ::new
       :parameters {:body-params {:person ::ows-person/new}}
       :responses {201 {:schema {:created ::ows-person/created}}}
       :roles #{:admin}
       :handler (fn [request]
                  (http-response/bad-request "TBD")
                  ;; (create request)
                  )
       }})))

