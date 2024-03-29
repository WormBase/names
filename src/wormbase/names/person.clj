(ns wormbase.names.person
  (:require
   [clojure.spec.alpha :as s]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [expound.alpha :refer [expound-str]]
   [ring.util.http-response :refer [bad-request created not-found!
                                    not-modified ok]]
   [spec-tools.core :as stc]
   [wormbase.db :as wdb]
   [wormbase.specs.person :as wsp]
   [wormbase.util :as wu]
   [wormbase.names.provenance :as wnp]
   [wormbase.specs.provenance :as wbsp]
   [wormbase.names.util :as wnu]))

(defmethod wnp/resolve-change :person/id
  [_ db change]
  (when-let [found (wnu/resolve-refs db (find change :person/id))]
    (assoc change
           :value
           (:person/id found))))

(defn create-person [request]
  (let [conn (:conn request)
        spec ::wsp/summary
        person (some-> request :body-params :data)
        transformed (stc/conform spec person stc/json-transformer)]
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
        (created (str "/person/" pid) person)))))

(defn pull-person [db lur]
  (d/pull db [:db/id
              :person/active?
              :person/email
              :person/id
              :person/name] lur))

(defn summary [db lur]
  (let [person (pull-person db lur)]
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
    (ok (wnu/unqualify-keys person "person"))))

(defn update-person
  "Handler for apply an update a person."
  [identifier request]
  (let [{db :db body-params :body-params} request
        lur (s/conform ::wsp/identifier identifier)
        payload (:data body-params)
        prov (wnp/assoc-provenance request payload :event/update-person)
        person (pull-person db lur)
        eid (:db/id person)]
    (when eid
      (let [spec ::wsp/update
            conn (:conn request)]
        (if (s/valid? spec payload)
          (if-let [data (some-> payload
                                (wnu/qualify-keys "person")
                                (assoc :db/id eid))]
            (let [tx-res @(d/transact-async conn [data prov])]
              (ok (-> tx-res
                      :db-after
                      (summary eid)
                      (wnu/unqualify-keys "person"))))
            (not-modified))
          (bad-request
           {:type :user/validation-error
            :problems (expound-str spec body-params)}))))))

(defn deactivate-person [identifier request]
  (let [{conn :conn db :db payload :body-params} request
        lur (s/conform ::wsp/identifier identifier)
        pull-person #(d/pull %
                             [:person/email :person/id :person/active?]
                             lur)
        person (pull-person db)
        active? (:person/active? person)]
    (when-not active?
      (not-found!))
    (let [prov (wnp/assoc-provenance request
                                     (:prov payload {})
                                     :event/deactivate-person)
          tx-result @(d/transact-async conn
                                       [[:db/cas lur :person/active? active? false] prov])]
      (when-let [dba (:db-after tx-result)]
        (-> (wu/elide-db-internals dba (pull-person dba))
            (wnu/unqualify-keys "person")
            (ok))))))

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
        :parameters {:body-params {:data ::wsp/summary :prov ::wbsp/provenance}}
        :responses (wnu/response-map created {:schema ::wsp/summary})
        :handler create-person}}))
   (sweet/context "/person/:identifier" []
     :tags ["person"]
     :path-params [identifier :- ::wsp/identifier]
     (sweet/resource
      {:get
       {:summary "Summarize a person."
        :x-name ::person-summary
        :responses (wnu/http-responses-for-read {:schema ::wsp/summary})
        :handler (wrap-id-validation about-person identifier)}
       :put
       {:summary "Update information about a person."
        :x-name ::update-person
        :responses (wnu/response-map ok {:schema ::wsp/summary})
        :parameters {:body-params {:data ::wsp/update
                                   :prov ::wbsp/provenance}}
        :handler (wrap-id-validation update-person identifier)}
       :delete
       {:summary "Deactivate a person."
        :x-name ::deactivate-person
        :responses (wnu/response-map ok {:schema ::wsp/summary})
        :handler (wrap-id-validation deactivate-person identifier)}}))))

