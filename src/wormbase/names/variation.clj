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
                                    not-found not-found!
                                    precondition-failed precondition-failed!]]
   [spec-tools.core :as stc]
   [wormbase.db :as wdb]
   [wormbase.ids.core :as wbids]
   [wormbase.names.auth :as wna]
   [wormbase.names.entity :as wne]
   [wormbase.names.matching :as wnm]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.common :as wsc]
   [wormbase.specs.provenance :as wsp]
   [wormbase.specs.variation :as wsv]))


(def identify (partial wne/identify ::wsv/identifier))

(def name-matching-rules
  '[[(variation-name ?pattern ?name ?eid ?attr)
     (matches-name :variation/name ?pattern ?name ?eid ?attr)]
    [(variation-name ?pattern ?name ?eid ?attr)
     (matches-name :variation/id ?pattern ?name ?eid ?attr)]])

(def status-changed-responses
  (-> wnu/default-responses
      (assoc ok {:schema ::wsv/status-changed})
      (wnu/response-map)))

(defn find-variations
  "Perform a prefix search against names in the DB.
  Match any unique variation identifier (name or id)."
  [request]
  (when-let [pattern (some-> request :query-params :pattern str/trim)]
    (let [db (:db request)
          matching-rules (concat wnm/name-matching-rules name-matching-rules)
          term (stc/conform ::wsv/find-term pattern)
          q-result (d/q '[:find ?vid ?vname
                          :in $ % ?term
                          :where
                          (variation-name ?term ?name ?eid ?attr)
                          [?eid :variation/id ?vid]
                          [(get-else $ ?eid :variation/name "") ?vname]]
                        db
                        matching-rules
                        (re-pattern (str "^" term)))
          res {:matches (or (some->> q-result
                                     (map (fn matched [[vid vname]]
                                            (merge {:variation/id vid}
                                                   (when (s/valid? :variation/name vname)
                                                     {:variation/name vname}))))
                                     (vec))
                            [])}]
      (ok res))))

(def info-pull-expr '[* {:variation/status [:db/ident]}])

(def summary (wne/summarizer identify info-pull-expr))

(def new-variation (wne/creator :variation/id ::wsv/new :event/new-variation info-pull-expr))

(def update-variation (wne/updater identify
                                   :variation/id
                                   ::wsv/update
                                   :event/update-variation
                                   info-pull-expr))

(def status-changer (partial wne/status-changer ::wsv/identifier :variation/status))

(def kill-variation (status-changer :variation.status/dead :event/kill-variation
                                    :fail-precondition? wnu/dead?
                                    :precondition-failure-msg "Variation to be killed is already dead."))

(def resurrect-variation (status-changer :variation.status/live :event/resurrect-variation
                                         :fail-precondition? wnu/live?
                                         :precondition-failure-msg "Variation is already live."))

(def coll-resources
  (sweet/context "/variation" []
    :tags ["variation"]
    (sweet/resource
     {:get
      {:summary "Find variations by any unique identifier."
       :parameters {:query-params ::wsc/find-request}
       :x-name ::find-variation
       :handler (fn find-by-any-identifier [request]
                  (find-variations request))}
      :post
      {:summary "Create a new variation."
       :x-name ::new-variation
       :middleware [wna/restrict-to-authenticated]
       :parameters {:body-params {:data ::wsv/new
                                  :prov ::wsp/provenance}}
       :responses (-> wnu/default-responses
                      (assoc created
                             {:schema {:created ::wsv/created}})
                      (assoc bad-request
                             {:schema ::wsc/error-response})
                      (wnu/response-map))
       :handler new-variation}})))

(def item-resources
  (sweet/context "/variation/:identifier" []
    :tags ["variation"]
    :path-params [identifier :- ::wsv/identifier]
    (sweet/resource
     {:delete
      {:summary "Kill a variation."
       :x-name ::kill-variation
       :middleware [wna/restrict-to-authenticated]
       :parameters {:body-params {:prov ::wsp/provenance}}
       :responses status-changed-responses
       :handler (fn [request]
                  (kill-variation request identifier))}
      :put
      {:summary "Update an existing variation."
       :x-name ::update-variation
       :parameters {:body-params {:data ::wsv/update
                                  :prov ::wsp/provenance}}
       :middleware [wna/restrict-to-authenticated]
       :responses (-> wnu/default-responses
                      (dissoc conflict)
                      (wnu/response-map))
       :handler update-variation}
      :get
      {:summary "Summarisef a variation."
       :x-name ::about-variation
       :responses (-> wnu/default-responses
                      (assoc ok {:schema ::wsv/info})
                      (wnu/response-map))
       
       :handler (fn [request]
                  (summary request identifier))}})
    (sweet/context "/resurrect" []
      (sweet/resource
       {:get
        {:summary "Find variations by ID or name."
         :x-name ::find-variations
         :handler find-variations}
        :post
        {:summary "Resurrect a variation."
         :x-name ::resurrect-variation
         :middleware [wna/restrict-to-authenticated]
         :respones status-changed-responses
         :handler (fn [request]
                    (resurrect-variation request identifier))}}))))

(def routes (sweet/routes coll-resources
                          item-resources))
