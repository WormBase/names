(ns wormbase.names.person
  (:require
   [clojure.spec.alpha :as s]
   [compojure.api.routes :as route]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [expound.alpha :refer [expound-str]]
   [ring.util.http-response :refer [bad-request created not-found! ok]]
   [spec-tools.core :as stc]
   [wormbase.db :as wdb]
   [wormbase.specs.person :as wsp]
   [wormbase.names.auth :as wna]
   [wormbase.util :as wu]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]))

(def admin-required! (partial wna/require-role! #{:person.role/admin}))

(defmethod wnp/resolve-change :person/id
  [attr db change]
  (when-let [found (wnu/resolve-refs db (find change :person/id))]
    (assoc change
           :value
           (:person/id found))))

(defn create-person [request]
  (admin-required! request)
  (let [conn (:conn request)
        spec ::wsp/summary
        person (some-> request :body-params)]
    (let [transformed (stc/conform spec person stc/json-transformer)]
      (if (s/invalid? transformed)
        (let [problems (expound-str  spec person)]
          (throw (ex-info "Invalid person data"
                          {:type :user/validation-error
                           :problems problems})))
        (let [prov (wnp/assoc-provenance request person :event/new-person)
              tx-data [(-> person
                           (wnu/qualify-keys "person")
                           (assoc :person/active? true)) prov]
              tx-res @(d/transact conn tx-data)
              pid (wdb/extract-id tx-res :person/id)]
          (created (str "/person/" pid) person))))))

(defn summary [db lur]
  (let [person (d/pull db '[*] lur)]
    (when (:db/id person)
      (wu/elide-db-internals db person))))

(defn about-person
  "Return summary about a WBPerson."
  [identifier request]
  (let [db (:db request)
        lur (s/conform ::wsp/identifier identifier)
        person (summary db lur)]
    (when-not person
      (not-found! {:identifier identifier}))
    (ok (-> (wnu/unqualify-keys person "person")
            (update :roles (fn [roles]
                             (map name roles)))))))

(defn update-person
  "Handler for apply an update a person."
  [identifier request]
  (admin-required! request)
  (let [{db :db body-params :body-params} request
        lur (s/conform ::wsp/identifier identifier)
        person (summary db lur)]
    (if person
      (let [spec ::wsp/summary
            conn (:conn request)]
        (if (s/valid? spec body-params)
          (let [data (some-> body-params
                             (wnu/qualify-keys "person")
                             (assoc :person/active? true))
                data* (if (empty? data)
                        data
                        (merge data
                               (when-not (:person/email data)
                                 (select-keys person [:person/email]))
                               (when-not (:person/id data)
                                 (select-keys person [:person/id]))))
                tx-res @(d/transact-async conn [data*])]
            (ok (-> tx-res
                    :db-after
                    (summary lur)
                    (wnu/unqualify-keys "person"))))
          (bad-request
           {:type :user/validation-error
            :problems (expound-str spec body-params)}))))))

(defn deactivate-person [identifier request]
  (admin-required! request)
  (let [{conn :conn db :db payload :body-params} request
        lur (s/conform ::wsp/identifier identifier)
        person (d/pull db [:person/email :person/active?] lur)
        active? (:person/active? person)]
    (when-not active?
      (not-found!))
    (let [prov (wnp/assoc-provenance request (:prov payload {}) :event/deactivate-person)
          tx-result @(d/transact-async conn
                                       [[:db/cas lur :person/active? active? false] prov])]
      (when-let [dba (:db-after tx-result)]
        (ok (wu/elide-db-internals dba (assoc person :person/active? false)))))))

(defn wrap-id-validation [handler identifier]
  (fn [request]
    (if (s/valid? ::wsp/identifier identifier)
      (handler identifier request)
      (throw (ex-info "Invalid person identifier"
                      {:type :user/validation-error
                       :problems (expound-str ::wsp/identifier identifier)})))))

(def routes
  (sweet/routes
   (sweet/context "/person" []
     :tags ["person"]
     (sweet/resource
      {:post
       {:summary "Create a new person."
        :x-name ::new-person
        :parameters {:body-params ::wsp/summary}
        :responses {201 {:schema ::wsp/summary}}
        :handler create-person}}))
   (sweet/context "/person/:identifier" []
     :tags ["person"]
     :path-params [identifier :- ::wsp/identifier]
     (sweet/resource
      {:get
       {:summary "Summaraise a person."
        :x-name ::person-summary
        :responses {200 {:schema ::wsp/summary}}
        :handler (wrap-id-validation about-person identifier)}
       :put
       {:summary "Update information about a person."
        :x-name ::update-person
        :responses {200 {:schema ::wsp/summary}}
        :parameters {:body-params ::wsp/update}
        :handler (wrap-id-validation update-person identifier)}
       :delete
       {:summary "Deactivate a person."
        :x-name ::deactivate-person
        :responses {200 {:schema ::wsp/summary}}
        :handler (wrap-id-validation deactivate-person identifier)}}))))

