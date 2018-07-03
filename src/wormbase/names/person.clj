(ns wormbase.names.person
  (:require
   [clojure.spec.alpha :as s]
   [compojure.api.routes :as route]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [wormbase.db :as wdb]
   [wormbase.names.auth.restructure :as wnar] ;; TBD: side effects?
   [wormbase.specs.person :as wsp]
   [wormbase.names.auth :as wna]
   [wormbase.names.util :as wnu]
   [wormbase.names.provenance :as wnp]
   [ring.util.http-response :as http-response]
   [spec-tools.core :as stc]))

(defn create-person [request]
  (let [conn (:conn request)
        spec ::wsp/person
        person (some-> request :body-params)]
    (let [transformed (stc/conform spec person stc/json-transformer)]
      (if (= transformed ::s/invalid)
        (let [problems (str (s/explain-data spec person))]
          (throw (ex-info "Invalid person data"
                          {:type :user/validation-error
                           :problems problems})))
        (let [tempid "datomic.tx"
              prov (wnp/assoc-provenence request person :event/new-person)
              tx-data [(assoc person :person/active? true) prov]
              tx-res @(d/transact conn tx-data)
              pid (wdb/extract-id tx-res :person/id)]
          (http-response/created (str "/person/" pid) person))))))

(defn about-person
  "Return info about a WBPerson."
  [identifier request]
  (let [db (:db request)
        lur (s/conform ::wsp/identifier identifier)
        person (wnu/entity->map (d/pull db '[*] lur))]
    (when (:db/id person)
      (http-response/ok person))))

(defn info [db lur]
  (let [person (d/pull db '[*] lur)]
    (when (:db/id person)
      (wnu/entity->map person))))

(defn update-person
  "Handler for apply an update a person."
  [identifier request]
  (let [db (:db request)
        lur (s/conform ::wsp/identifier identifier)
        person (info db lur)]
    (if (:db/id person)
      (let [spec ::wsp/person
            conn (:conn request)
            data (some-> request
                         :body-params
                         (assoc :person/active? true))
            data* (if (empty? data)
                    data
                    (merge data
                           (when-not (:person/email data)
                             (select-keys person [:person/email]))
                           (when-not (:person/id data)
                             (select-keys person [:person/id]))))]
        (if (s/valid? spec data*)
          (let [tx-res @(d/transact-async conn [data*])]
            (http-response/ok (info (:db-after tx-res) lur)))
          (http-response/bad-request
           {:type :user/validation-error
            :problems (s/explain-data spec data*)}))))))

(defn deactivate-person [identifier request]
  (let [conn (:conn request)
        lur (s/conform ::wsp/identifier identifier)
        tx-result @(d/transact-async conn
                                     [[:wormbase.tx-fns/deactivate-person lur]])]
    (http-response/ok)))
  
(defn wrap-id-validation [handler identifier]
  (fn [request]
    (if (s/valid? ::wsp/identifier identifier)
      (handler identifier request)
      (throw (ex-info "Invalid person identifier"
                      {:type :user/validation-error
                       :problems (s/explain-data ::wsp/identifier identifier)})))))

(def routes
  (sweet/routes
   (sweet/context "/person/" []
     :tags ["person"]
     :roles #{:person.role/admin}
     (sweet/resource
      {:coercion :spec
       :post
       {:summary "Create a new person."
        :x-name ::new-person
        :parameters {:body-params ::wsp/person}
        :responses {201 {:schema ::wsp/person}}
        :roles #{:admin}
        :handler create-person}}))
   (sweet/context "/person/:identifier" [identifier]
     :tags ["person"]
     :roles #{:person.role/admin}
     (sweet/resource
      {:coercion :spec
       :get
       {:summary "Information about a person."
        :x-name ::about-person
        :path-params [identifier :- ::wsp/identifier]
        :handler (wrap-id-validation about-person identifier)}
       :put
       {:summary "Update information about a person."
        :x-name ::update-person
        :path-params [identifier :- ::wsp/identifier]
        :parameters {:body-params ::wsp/person}
        :handler (wrap-id-validation update-person identifier)}
       :delete
       {:summary "Deactivate a person."
        :x-name ::deactivate-person
        :path-params [identifier :- ::wsp/identifier]
        :handler (wrap-id-validation deactivate-person identifier)}}))))

