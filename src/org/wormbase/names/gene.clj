(ns org.wormbase.names.gene
  (:require
   [clj-time.coerce :refer [to-date]]
   [clj-time.core :as ct]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [ring.util.http-response :as resp]
   [spec-tools.spec :as st]
   [spec-tools.core :as stc]
   [org.wormbase.names.util :refer [namespace-keywords]]
   [org.wormbase.specs.gene :as gene-specs]
   [org.wormbase.specs.common :as common]))

(defn- extract-ids [tx-result]
  (->> (map :e (:tx-data tx-result))
       (map (partial d/entity (:db-after tx-result)))
       (map :gene/id)
       (filter identity)
       (set)
       (vec)))

(defn- result-seq [new-ids]
  (->> new-ids
       (interleave (repeat (count new-ids) :gene/id))
       (partition 2)
       (map (partial apply hash-map))
       (vec)))

(defn resolve-ref
  "Resolve a ref to its keyword name/ident."
  [env kw v]
  (if (keyword? v)
    (keyword (name kw) (name v))
    (when-let [db (:db env)]
      (let [domain (name kw)
            entity-ident (keyword domain "id")
            lookup-ref [entity-ident]]
        (some->> (str/split v #"/")
                 (last)
                 (keyword (name kw))
                 (conj lookup-ref)
                 (d/entity db)
                 (entity-ident))))))

(def ^{:doc (str "A dispatch map of keys in name records "
                 "to how their value should be inferred by "
                 "the request handler.")}
  ingress-processors
  {:gene/species resolve-ref
   :gene/biotype resolve-ref})

;; (s/fdef pre-process
;;         :args )
(defn pre-process [request domain name-records]
  (vec (map #(reduce-kv (fn [rec kw v]
                          (if-let [v-fn (kw ingress-processors)]
                            (assoc rec kw (v-fn request kw v))
                            rec))
                        %
                        %)
            (namespace-keywords domain name-records))))

(defn create-new-names [request]
  ;; TODO: "who" needs to come from auth
  ;;      problably should ditch WBPerson ids in favour of emails
  ;;      -in sanger-nameserver data, "log_who" is always a staff
  ;;       member. So instead of :person/id it should be :staff/id
  (let [who (d/entity (:db request)
                      [:user/email
                       "matthew.russell@wormbase.org"])
        xform (partial pre-process request "gene")
        name-records (some-> request :body-params :new xform)
        spec ::gene-specs/names-new
        txes [[:wb.dbfns/new-names "gene" name-records spec]
              {:db/id (d/tempid :db.part/tx)
               :provenance/who (:db/id who)

               ;; TODO: application/how should come from a http header?
               ;;       e.g: User-Agent
               :provenance/how :user.agent/script

               ;; TODO: "when" potentially should come from the data,
               ;;       eps. when importing (might need to abstract)
               ;;       ct/now is not right: might aswell use
               ;;       :db/txInstant
               :provenance/when (to-date (ct/now))}]]
    (let [tx-result @(d/transact (:conn request) txes)
          new-ids (extract-ids tx-result)
          result {:names-created (result-seq new-ids)}]
      (resp/created "/gene/" result))))

(defn update-names [request gene-id]
  (let [conn (:conn request)
        db (:db request)
        entity (d/entity db [:gene/id gene-id])]
    (if entity
      (let [eid (:db/id entity)
            xform (partial pre-process request)
            name-records (some-> request :body-params :add xform)
            who (d/entity db
                          [:user/email "matthew.russell@wormbase.org"])]
        @(d/transact conn [[:wb.dbfns/update-names eid name-records]]))
      (resp/not-found (format "Gene '%s' does not exist" gene-id)))))

(defn responses-map
  [success-code success-spec]
  (let [err-codes (range 400 501)
        default (->> {:schema ::common/error-response}
                     (repeat (count err-codes))
                     (interleave err-codes)
                     (apply sorted-map))
        response-map (assoc default
                            success-code
                            {:schema success-spec})]
    response-map))

(def routes
  (sweet/routes
   (sweet/context "/gene" []
     :tags ["gene"]
     (sweet/resource
      {:coercion :spec
       :post
       {:summary "Create new names for a gene (cloned or un-cloned)"
        :parameters {:body ::gene-specs/names-new-request}
        :responses {201 {:schema (s/keys :req-un [::gene-specs/names-created])}
                    400 {:schema  ::common/error-response}
                    }
        :handler create-new-names}}))
   (sweet/context "/gene/:id" []
     :tags ["gene"]
     :path-params [id :- :gene/id]
     (sweet/resource
      {:coercion :spec
       :put
       {:summary "Add new names to an existing gene"
        :parameters {:body-params ::gene-specs/names-update-request}
        :responses {200 {:schema (s/keys :req-un [::gene-specs/names-updated])}
                    400 {:schema ::common/error-response}}
        :handler (fn [request]
                   (update-names request id))}}))))
