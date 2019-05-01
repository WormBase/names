(ns wormbase.names.species
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [ring.util.http-response :refer [created ok]]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]
   [wormbase.db :as wdb]
   [wormbase.names.auth :as wna]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.provenance :as wsp]
   [wormbase.specs.species :as wss]))

(s/def ::identifier (stc/spec {:spec (s/and sts/string? #(re-matches wss/id-regexp %))
                               :swagger/example "c-elegans"
                               :description "lower-case hyphen seperated abbriviation. e.g: \"c-elegans\"."}))

(defn latin-name->id [ln]
  (let [[head tail] (-> ln str/lower-case (str/split  #" " 2))]
    (->> (str/replace tail #"\s" "")
         (str (first head) "-")
         (keyword "species"))))

(def summary-pull-expr '[*])

(defn handle-new [request]
  (let [{payload :body-params db :db conn :conn} request
        {data :data prov :prov} payload
        cdata (wnu/conform-data ::wss/new data)
        id (-> cdata :species/latin-name latin-name->id)
        prov (wnp/assoc-provenance request payload :event/new-species)
        tx-data [(assoc cdata :species/id id) prov]
        tx-res @(d/transact-async conn tx-data)]
    (when-let [dba (:db-after tx-res)]
      (created (str "/species/" id) {}))))

(defn handle-update
  [request identifier]
  (let [{payload :body-params db :db conn :conn} request
        {data :data prov :prov} payload
        species-id (keyword "species" identifier)
        cdata (wnu/conform-data ::wss/update data)
        prov (wnp/assoc-provenance request payload :event/update-species)
        tx-data [['wormbase.ids.core/cas-batch [:species/id species-id] cdata]
                 prov]
        tx-res @(d/transact-async conn tx-data)]
    (when-let [dba (:db-after tx-res)]
      (ok))))

(def coll-resources
  (sweet/context "/species" []
    :tags ["species"]
    (sweet/resource
     {:post
      {:summary "Add a new species to the system."
       :x-name ::new-species
       :middleware [wna/restrict-to-authenticated]
       :parameters {:body-params {:data ::wss/new
                                  :prov ::wsp/provenance}}
       :handler handle-new}})))

(def item-resources
  (sweet/context "/species/:identifier" []
    :tags ["species"]
    :path-params [identifier :- string?]
    (sweet/resource
     {:get
      {:summary "Species details held in the system."
       :x-name ::species-summary
       :handler (fn handle-summary [request]
                  (let [sid (s/conform ::identifier identifier)
                        lur [:species/id (keyword "species" sid)]]
                    (-> (:db request)
                        (wdb/pull summary-pull-expr lur)
                        ok)))}
      :put
      {:summary "Update species details."
       :x-name ::species-update
       :parameters {:body-params {:data ::wss/update
                                  :prov ::wsp/provenance}}
       :handler (fn [request]
                  (try
                    (handle-update request identifier)
                    (catch Exception exc
                      (prn exc)
                      (throw exc))))}})))

(def routes (sweet/routes coll-resources item-resources))

(comment
  (latin-name->id "Matt Russell"))
