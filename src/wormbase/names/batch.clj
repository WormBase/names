(ns wormbase.names.batch
  (:require
   [clojure.spec.alpha :as s]
   [datomic.api :as d]
   [compojure.api.sweet :as sweet]
   [ring.util.http-response :refer [bad-request
                                    conflict
                                    created
                                    not-found not-found!
                                    ok]]
   [spec-tools.spec :as sts]
   [wormbase.names.auth :as wna]
   [wormbase.names.util :as wnu]
   [wormbase.specs.batch :as wsb]
   [wormbase.specs.gene :as wsg]
   [wormbase.specs.common :as wsc]
   [wormbase.specs.provenance :as wsp]
   [wormids.batch :as wb]
   [wormbase.names.batch :as wnb]))

(defn find-entities [request]
  (bad-request "TBD"))

(defn batcher [impl entity-type spec request]
  (let [{conn :conn payload :body-params} request
        {data :data prov :prov} payload
        cdata (map second (wnu/conform-data request spec data))
        uiident (keyword entity-type "id")]
    (impl conn uiident cdata prov)))

(defn new-entities
  "Create a batch of new entities.

  Status codes and meaning:
  404 - Non-existent reference to entity.
  201 - Created all entities successfully."
  [entity-type request]
  (let [result (batcher wb/new entity-type ::wsb/new request)]
    (created (-> result :batch/id str) {:created result})))

(defn update-entities
  [entity-type request]
  (let [result (batcher wb/update entity-type ::wsb/update request)]
    (ok {:updated result})))

(s/def ::entity-type sts/string?)

(def resources
  (sweet/context "/batch/:entity-type" []
    :tags ["batch"]
    :path-params [entity-type :- ::entity-type]
    (sweet/resource
    {:get
     {:summary "Find entities by any name."
      :x-name ::find-entities
      :responses (-> wnu/default-responses
                     (assoc ok {:schema ::wsc/find-result})
                     (wnu/response-map))
      :parameters {:query-params ::wsc/find-request}
      :handler (fn find-by-any-name [request]
                 ;; We know the entity type, can we use it to ompitise query?
                 (find-entities request))}
     :put
     {:summary "Update entity records."
      :x-name ::update-entities
      :middleware [wna/restrict-to-authenticated]
      :responses (-> wnu/default-responses
                     (dissoc conflict)
                     (assoc ok {:schema {:updated ::wsb/updated}})
                     (wnu/response-map))
      :parameters {:body-params {:prov ::wsp/provenance :data ::wsb/update}}
      :handler (fn foo [request]
                 (update-entities entity-type request))}
     :post
     {:summary "Assign identifiers and associate names, creating new entities."
      :x-name ::new-entities
      :middleware [wna/restrict-to-authenticated]
      :responses (-> wnu/default-responses
                     (assoc created {:schema {:created ::wsb/created}})
                     (assoc bad-request {:schema ::wsc/error-response})
                     (wnu/response-map))
      :parameters {:body-params {:data ::wsb/new :prov ::wsp/provenance}}
      :handler (partial new-entities entity-type)}
    })))

;; (sweet/context ":attr" []
;;   :tags ["batch"]
;;   :path-params [attr :- ::wsb/name-attr]
;;   (sweet/resource
;;    {:delete
;;     {:sumamry "Remove names from genes."
;;      :x-name ::remove-entity-names
;;      :parameters {:body-params [:data ::wsb/remove-names]}
;;      :responses wnu/default-responses
;;      :handler (fn remove-names [request]
;;                 (let [{conn :conn payload :body-params} request
;;                       {data :data prov :prov} payload
;;                       result (wb/remove-names conn :gene/id attr data prov)]
;;                   (ok result)))}}))



;;;; /api/batch/<type>/ POST -> new
;;;; /api/batch/<type>/ PUT -> update
;;;; /api/batch/<type>/ DELETE -> change-status=deleted
;;;; /api/batch/<type>/retract/ POST -> change-status=retracted
;;;; /api/batch/<type>/suppress/ POST -> change-status=suppressed
;;;; /api/batch/<type>/?q= GET -> find-by-any-name-across-types
;;;; /api/batch/<type>/<batch-id>/ DELETE -> undo-batch-operation
;;;; /api/batch/<type>/<batch-id>/ GET -> batch-info

(def routes (sweet/routes resources))
