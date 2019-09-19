(ns wormbase.names.variation
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [expound.alpha :refer [expound-str]]
   [java-time :as jt]
   [ring.util.http-response :refer [ok
                                    bad-request
                                    conflict
                                    created
                                    not-found not-found!]]
   [spec-tools.core :as stc]
   [wormbase.db :as wdb]
   [wormbase.ids.core :as wbids]
   [wormbase.names.entity :as wne]
   [wormbase.names.matching :as wnm]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.common :as wsc]
   [wormbase.specs.provenance :as wsp]
   [wormbase.specs.variation :as wsv]))

(def identify (partial wne/identify ::wsv/identifier))

(def status-changed-responses
  (-> wnu/default-responses
      (assoc ok {:schema ::wsv/status-changed})
      (wnu/response-map)))

(def summary-pull-expr '[* {:variation/status [:db/ident]}])

(def coll-resources
  (sweet/context "/variation" []

    :tags ["variation"]
    (sweet/resource
     {:get
      {:summary "Find variations by any unique identifier."
       :parameters {:query-params ::wsc/find-request}
       :x-name ::find-variation
       :handler (wne/finder "variation")}
      :post
      {:summary "Create a new variation."
       :x-name ::new-variation
       :parameters {:body-params {:data ::wsv/new
                                  :prov ::wsp/provenance}}
       :responses (-> wnu/default-responses
                      (assoc created
                             {:schema {:created ::wsv/created}})
                      (assoc bad-request
                             {:schema ::wsc/error-response})
                      (wnu/response-map))
       :handler (fn handle-new [request]
                  (let [new-variation (wne/creator :variation/id
                                                   (partial wnu/conform-data ::wsv/new)
                                                   :event/new-variation
                                                   summary-pull-expr)]
                    (new-variation request)))}})))

(def item-resources
  (sweet/context "/variation/:identifier" []
    :tags ["variation"]
    :path-params [identifier :- ::wsv/identifier]
    (sweet/resource
     {:delete
      {:summary "Kill a variation."
       :x-name ::kill-variation
       :parameters {:body-params {:prov ::wsp/provenance}}
       :responses status-changed-responses
       :handler (fn [request]
                  (let [kill (wne/status-changer
                              :variation/id
                              :variation/status
                              :variation.status/dead
                              :event/kill-variation
                              :fail-precondition? wnu/dead?
                              :precondition-failure-msg "Variation to be killed is already dead.")]
                  (kill request identifier)))}
      :put
      {:summary "Update an existing variation."
       :x-name ::update-variation
       :parameters {:body-params {:data ::wsv/update
                                  :prov ::wsp/provenance}}
       :responses (-> wnu/default-responses
                      (dissoc conflict)
                      (wnu/response-map))
       :handler (fn handle-variation-update [request]
                  (let [update-variation (wne/updater identify
                                                      :variation/id
                                                      (partial wnu/conform-data ::wsv/update)
                                                      :event/update-variation
                                                      summary-pull-expr)]
                    (update-variation request identifier)))}
      :get
      {:summary "Summarise a variation."
       :x-name ::about-variation
       :responses (-> wnu/default-responses
                      (assoc ok {:schema ::wsv/summary})
                      (wnu/response-map))
       
       :handler (fn [request]
                  (let [hs (wne/summarizer identify summary-pull-expr #{})]
                    (hs request identifier)))}})
    (sweet/context "/resurrect" []
      (sweet/resource
       {:post
        {:summary "Resurrect a variation."
         :x-name ::resurrect-variation
         :respones status-changed-responses
         :handler (fn [request]
                    (let [resurrect (wne/status-changer
                                     :variation/id
                                     :variation/status
                                     :variation.status/live
                                     :event/resurrect-variation
                                     :fail-precondition? wnu/live?
                                     :precondition-failure-msg "Variation is already live.")]
                      (resurrect request identifier)))}}))))

(def routes (sweet/routes coll-resources
                          item-resources))
