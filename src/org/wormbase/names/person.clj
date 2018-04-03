(ns org.wormbase.names.person
  (:require
   [clojure.spec.alpha :as s]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [org.wormbase.db :as owdb]
   [org.wormbase.names.auth.restructure] ;; TBD: side effects?   
   [org.wormbase.specs.person :as owsp]
   [org.wormbase.names.util :as ownu]
   [ring.util.http-response :as http-response]
   [org.wormbase.names.provenance :as ownp]
   [compojure.api.routes :as routes]))

(defn create-person [request]
  (let [conn (:conn request)
        spec ::owsp/person
        person (some-> request :body-params)]
    (if (s/valid? spec person)
      (let [tempid "datomic.tx"
            prov (ownp/assoc-provenence request person :event/new-person)
            tx-res @(d/transact conn [person prov])
            pid (owdb/extract-id tx-res :person/id)]
        (http-response/created (str "/person/" pid) person))
      (let [problems (s/explain-data spec person)]
        (throw (ex-info "Invalid person data"
                        {:type :user/validation-error
                         :problems problems}))))))

(defn about
  "Return info about a WBPerson."
  [request identifier]
  (when (s/valid? ::owsp/identifier identifier)
    (let [db (:db request)
          lur (s/conform ::owsp/identifier identifier)
          person (ownu/entity->map (d/pull db '[*] lur))]
      (when (:db/id person)
        (http-response/ok person)))))

(def routes
  (sweet/routes
   (sweet/context "/person/" []
     :tags ["person"]
     (sweet/resource
      {:coercion :spec
       :post
       {:summary "Create new persons."
        :x-name ::new-person
        :parameters {:body ::owsp/person}
        :responses {201 {:schema ::owsp/person}}
        :roles #{:admin}
        :handler create-person}}))
   (sweet/context "/person/:identifier" [identifier]
     :tags ["person"]
     (sweet/resource
      {:coercion :spec
       :get
       {:summary "Information about a WB Person."
        :x-name ::about-person
        :path-params [identifier :- ::owsp/identifier]
        :handler (fn about-person [request]
                   (about request identifier))}}))))

