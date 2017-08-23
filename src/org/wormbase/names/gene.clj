(ns org.wormbase.names.gene
  (:require
   [clj-time.coerce :refer [to-date]]
   [clj-time.core :as ct]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [compojure.api.sweet :refer [context resource]]
   [datomic.api :as d]
   [org.wormbase.specs.gene :as gene-specs]
   [ring.util.http-response :refer [bad-request created
                                    ok internal-server-error]]
   [spec-tools.spec :as st]
   [clojure.spec.test.alpha :as stest]))

(defn- extract-ids [tx-result]
  (->> (map :e (:tx-data tx-result))
       (map (partial d/entity (:db-after tx-result)))
       (map :gene/id)
       (filter identity)
       (set)
       (vec)))

(defn- result-seq [new-ids]
  (->> (interleave (repeat (count new-ids) :gene/id) new-ids)
       (partition 2)
       (map (partial apply hash-map))
       (vec)))

(defn resolve-ref
  "Resolve a ref to its keyword name/ident."
  [env kw v]
  (when-let [db (:db env)]
    (let [domain (name kw)
          entity-ident (keyword domain "id")
          lookup-ref [entity-ident]]
      (some->> (str/split v #"/")
               (last)
               (keyword (name kw))
               (conj lookup-ref)
               (d/entity db)
               (entity-ident)))))

(def ^{:doc (str "A dispatch map of keys in name records "
                 "to how their value should be inferred by "
                 "the request handler.")}
  ingress-processors
  {:gene/species resolve-ref
   :gene/biotype resolve-ref})

;; (s/fdef pre-process
;;         :args )
(defn pre-process [request name-records]
  (vec (map #(reduce-kv (fn [rec kw v]
                          (if-let [v-fn (kw ingress-processors)]
                            (assoc rec kw (v-fn request kw v))
                            rec))
                        %
                        %)
            name-records)))

(defn handler [request]
  ;; TODO: "who" needs to come from auth
  ;;      problably should ditch WBPerson ids in favour of emails
  ;;      -in sanger-nameserver data, "log_who" is always a staff
  ;;       member. So instead of :person/id it should be :staff/id
  (let [who (d/entity (:db request)
                      [:user/email
                       "matthew.russell@wormbase.org"])
        xform (partial pre-process request)
        name-records (some-> request :body-params :new xform)
        spec ::gene-specs/new-names
        txes [[:wb.dbfns/new-names "gene" name-records spec]
              {:db/id (d/tempid :db.part/tx)
               :provenance/who (:db/id who)

               ;; TODO: application/how should come from a http header?
               ;;       e.g: User-Agent
               :provenance/how :user.agent/script
               
               ;; TODO: "when" protentially should come from the data,
               ;;       eps. when importing (might need to abstract)
               ;;       ct/now is not right: might aswell use
               ;;       :db/txInstant
               :provenance/when (to-date (ct/now))}]]
    ;; (println "TXES:")
    ;; (clojure.pprint/pprint txes)
    (try
      (let [tx-result @(d/transact (:conn request) txes)
            new-ids (extract-ids tx-result)
            result {:created (result-seq new-ids)}]
        (created "/gene/" result))
      (catch Exception exc
        (let [info (ex-data exc)]
          (if info
            (bad-request {:problems "Request was invalid"})
            (internal-server-error {:problems
                                    (pr-str exc)})))))))

(defn handle-update [request]
  ;; TODO:
  nil)

(def gene-names
  (resource
   {:coercion :spec
    :post
    {:summary "Create new names for a gene (cloned or un-cloned)"
     :parameters {:body-params ::gene-specs/new-names-request}
     :responses {201 {:schema ::gene-specs/created}}
     :handler handler}}))

(def routes
  (context "/gene" []
    :tags ["gene"]
    :coercion :spec
    gene-names))
