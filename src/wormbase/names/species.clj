(ns wormbase.names.species
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [ring.util.http-response :refer [bad-request! created ok]]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]
   [wormbase.db :as wdb]
   [wormbase.names.entity :as wne]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.provenance :as wsp]
   [wormbase.specs.species :as wss]))

(defn latin-name->id [ln]
  (let [[head tail] (-> ln str/lower-case (str/split  #" " 2))]
    (->> (str/replace tail #"\s" "")
         (str (first head) "-")
         (keyword "species"))))

(defn new-item [request]
  (let [{payload :body-params db :db conn :conn} request
        {data :data prov :prov} payload]
    (when-not (s/valid? ::wss/new data)
      (bad-request! data))
    (let [cdata (wnu/qualify-keys (wnu/conform-data ::wss/new data) "species")
          id (-> cdata :species/latin-name latin-name->id)
          prov (wnp/assoc-provenance request payload :event/new-species)
          tx-data [(assoc cdata :species/id id) prov]
          tx-res @(d/transact-async conn tx-data)]
      (when-let [dba (:db-after tx-res)]
        (created (str "/species/" (name id))
                 (-> dba
                     (d/pull [:species/latin-name] [:species/id id])
                     (wnu/unqualify-keys "species")))))))

(defn update-item
  [request identifier]
  (let [{payload :body-params db :db conn :conn} request
        {data :data prov :prov} payload
        species-id (keyword "species" identifier)
        cdata* (wnu/conform-data ::wss/update data) 
        cdata (wnu/qualify-keys cdata* "species")
        prov (wnp/assoc-provenance request payload :event/update-species)
        tx-data [['wormbase.ids.core/cas-batch [:species/id species-id] cdata]
                 prov]
        tx-res @(d/transact-async conn tx-data)]
    (when-let [dba (:db-after tx-res)]
      (ok
       (wnu/unqualify-keys
        (d/pull dba [:species/latin-name] [:species/id species-id])
        "species")))))

(defn list-items
  [request]
  (ok (->> (d/q '[:find [(pull ?e pattern) ...]
                  :in $ pattern
                  :where
                  [?e :species/id _]]
                (:db request)
                [:species/id
                 :species/latin-name
                 :species/cgc-name-pattern
                 :species/sequence-name-pattern])
           (map #(wnu/unqualify-keys % "species"))
           (map (fn humanize-vals [sp]
                  (reduce-kv (fn [m k v]
                               (assoc m k (wnu/unqualify-maybe v)))
                             {}
                             sp))))))

(def coll-resources
  (sweet/context "/species" []
    :tags ["species"]
    (sweet/resource
     {:get
      {:summary "Retrieve of attributes held for each species in system."
       :x-name ::list-species
       :responses (wnu/http-responses-for-read {:schema ::wss/listing})
       :handler (fn li [request]
                  (list-items request))}
      :post
      {:summary "Add a new species to the system."
       :x-name ::new-species
       :responses (wnu/response-map created {:schema ::wss/created})
       :parameters {:body-params {:data ::wss/new
                                  :prov ::wsp/provenance}}
       :handler new-item}})))

(def item-resources
  (sweet/context "/species/:identifier" []
    :tags ["species"]
    :path-params [identifier :- string?]
    (sweet/resource
     {:get
      {:summary "Species details held in the system."
       :x-name ::species-summary
       :responses (wnu/http-responses-for-read {:schema ::wss/item})
       :handler (fn handle-summary [request]
                  (let [summarize (wne/summarizer (partial wne/identify ::wss/identifier "species")
                                                  '[*]
                                                  #{})]
                    (summarize request identifier)))}
      :put
      {:summary "Update species details."
       :x-name ::species-update
       :responses (wnu/response-map ok {:schema ::wss/updated})
       :parameters {:body-params {:data ::wss/update
                                  :prov ::wsp/provenance}}
       :handler (fn handle-update [request]
                  (update-item request identifier))}})))

(def routes (sweet/routes coll-resources item-resources))
