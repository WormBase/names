(ns wormbase.names.batch
  (:require
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
   [wormbase.specs.common :as wsc]
   [wormbase.specs.provenance :as wsp]   
   [wormids.batch :as wb]))

(defn find-entities [request]
  (bad-request "TBD"))

(defn batcher [impl entity-type request]
  (let [{conn :conn payload :body-params} request
        {data :data prov :prov} payload
        uiident (keyword entity-type "id")]
    (impl conn uiident data prov)))

(def new-entities [entity-type request]
  (let [result (batcher wb/new entity-type request)
        uri (str (or (:batch/id result)
                     ((keyword entity-type "id") result))]
    (created uri result))))

(def update-entities [entity-type request]
  (ok (batcher entity-type request)))

(def resources
  (sweet/context "/:entity-type/batch" []
    :tags ["batch"]
    :path-params [entity-type :- sts/string?]
    (sweet/resource
     {:get
      {:summary "Find entities by any name."
       :responses (-> wnu/default-responses
                      (assoc ok {:schema ::wsc/find-result})
                      (wnu/response-map))
       :parameters {:query-params ::wsc/find-request}
       :x-name ::find-entities
       :handler (fn find-by-any-name [request]
                  ;; We know the entity type, can we use it to ompitise query?
                  (find-entities request))}
      :post
      {:summary "Assign identifiers and associate names, creating new entities."
       :middleware [wna/restrict-to-authenticated]
       :x-name ::new-entities
       :parameters {:body-params {:data ::wsb/new :prov ::wsp/provenance}}
       :responses (-> wnu/default-responses
                      (assoc created {:schema {:created ::wsb/created}})
                      (assoc bad-request {:schema ::wsc/error-response})
                      (wnu/response-map))
       :handler (partial new-entities entity-type)}
      :put
      {:summary "Update entity records."
       :middleware [wna/restrict-to-authenticated]
       :x-name ::update-entitites
       :responses (-> wnu/default-responses
                      (dissoc conflict)
                      (wnu/response-map))
       :handler (partial update-entities entity-type)}})
    (sweet/context "/remove-names" []
      :tags ["batch"]
      (sweet/POST "" []
        :path-params [attr :- ::wsb/name-attr]
        :parameters {:body-params [:data ::wsb/remove-names]}
        :responses wnu/default-responses
        (let [{conn :conn payload :body-params} request
              attr
              result (wb/remove-names conn)]))
      )))


;;;; /api/batch/<type>/ POST -> new
;;;; /api/batch/<type>/ PUT -> update
;;;; /api/batch/<type>/ DELETE -> change-status=deleted
;;;; /api/batch/<type>/retract/ POST -> change-status=retracted
;;;; /api/batch/<type>/suppress/ POST -> change-status=suppressed
;;;; /api/batch/<type>/?q= GET -> find-by-any-name-across-types
;;;; /api/batch/<type>/<batch-id>/ DELETE -> undo-batch-operation
;;;; /api/batch/<type>/<batch-id>/ GET -> batch-info

(def routes (sweet/routes resources))
