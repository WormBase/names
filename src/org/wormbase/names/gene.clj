(ns org.wormbase.names.gene
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [ring.util.http-response :as resp]
   [spec-tools.spec :as st]
   [spec-tools.core :as stc]
   [org.wormbase.names.auth :as own-auth]
   [org.wormbase.names.provenance :as provenance]
   [org.wormbase.names.util :refer [namespace-keywords]]
   [org.wormbase.specs.common :as owsc]
   [org.wormbase.specs.gene :as owsg]
   [org.wormbase.specs.auth :as oswa]
   [spec-tools.core :as stc]
   [spec-tools.spec :as sts]
   [ring.util.http-response :as http-response]
   [java-time :as jt]))

(defn- extract-id [tx-result]
  (some->> (map :e (:tx-data tx-result))
           (map (partial d/entity (:db-after tx-result)))
           (map :gene/id)
           (filter identity)
           (set)
           (vec)
           (first)))

(defn- result-seq [new-ids]
  (some->> new-ids
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

;; (def ^{:doc (str "A dispatch map of keys in name records "
;;                  "to how their value should be inferred by "
;;                  "the request handler.")}
;;   ingress-processors
;;   {:gene/species resolve-ref
;;    :gene/biotype resolve-ref})

;; ;;; TODO: spec this function?
;; ;; (s/fdef pre-process
;; ;;         :args )
;; (defn pre-process [request domain name-records]
;;   (vec (map #(reduce-kv (fn [rec kw v]
;;                           (if-let [v-fn (kw ingress-processors)]
;;                             (assoc rec kw (v-fn request kw v))
;;                             rec))
;;                         %
;;                         %)
;;             (namespace-keywords domain name-records))))

;; (defn resolve-refs [name-record]
;;   (reduce-kv #(update %1 %2 (get ingress-processors %2))
;;              (empty name-record)
;;              name-record))

(defn having-key-ns [data key-ns]
  (into {} (filter #(= (namespace (key %)) key-ns)) data))

(defn ap [request payload]
  (let [prov (having-key-ns payload "provenance")
        who (:provenance/who prov)]
    (if-not (:db request)
      (throw (ex-info "DB was nil!" {})))
    (cond-> {}
      ;; (nil? (:provenance/who prov))
      ;; (assoc :provenance/who "whof")

      (:user/email who)
      (assoc :provenance/who (:db/id
                              (d/entity (:db request)
                                        [:user/email (:user/email who)])))

      ;; (nil? (:provenance/why prov))
      ;; (assoc :provenance/why "whyf")

      (nil? (:provenance/when prov))
      (assoc :provenance/when (jt/to-java-date (jt/instant)))

      ;; (nil? (:provenance/how prov))
      ;;(assoc :provenance/how "TBD")
      )))

(defn new-names [request]
  ;; TODO: "who" needs to come from auth
  ;;      problably should ditch WBPerson ids in favour of emails
  ;;      -in sanger-nameserver data, "log_who" is always a staff
  ;;       member. So instead of :person/id it should be :staff/id
  (let [data (some-> request :body-params :new)
        name-record (having-key-ns data "gene")
        prov (assoc (ap request data)
                    :db/id "datomic.tx")
        nnn (println "NAME-REC:" name-record)
        ppp (println "PROV:" prov)
        spec ::owsg/new
        txes [[:wormbase.tx-fns/new-name "gene" name-record spec]
              prov]
        ;; yyy (println "Submitting txes:" (pr-str txes))
        ]
    (let [tx-result @(d/transact (:conn request) txes)
          new-id (extract-id tx-result)
          result {:created {:gene/id new-id}}]
      (resp/created "/gene/" result))))

(defn update-names [request gene-id]
  (let [conn (:conn request)
        db (:db request)
        lur [:gene/id gene-id]
        entity (d/entity db lur)]
      (if entity
        (let [data (some-> request :body-param :add)
              name-record (having-key-ns data "gene")
              prov (ap request data)
              tx-result @(d/transact
                          conn
                          [[:wormbase.tx-fns/update-name
                            lur
                            name-record
                            ::owsg/update]
                           prov])
              yyy (print "after transact")]
          (resp/ok {:updated (pr-str tx-result)}))
        (do
          (println "***************** NO GENE FOUND ***********************")
          (resp/not-found (format "Gene '%s' does not exist" gene-id))))))

(defn merge-into [request]
  ;; If both the source and targets of the merge have CGC names
  ;; abort with a HTTP conflict
  ;; See: /wormbase-pipeline/scripts/NAMEDB/lib/NameDB_handler.pm
  ;;      for reference (!)
  ;; 
  nil)

(defn idents-by-ns [db ns-name]
  (sort (d/q '[:find [?ident ...]
               :in $ ?ns-name
               :where
               [?e :db/ident ?ident]
               [_ :db.install/attribute ?e]
               [(namespace ?ident) ?ns]
               [(= ?ns ?ns-name)]]
             db ns-name)))

(defn merge-genes
  [db user id-spec src-id target-id new-biotype]
  (if (every? (map (partial s/valid? id-spec) [src-id target-id]))
    (let [invoke (partial d/invoke db)]
      (when (= src-id target-id)
        (throw (ex-info "Cannot merge gene into itself!"
                        {:src-id src-id
                         :target-id target-id})))
      (when-not (s/valid? :gene/biotype new-biotype)
        (throw (ex-info "Invalid target biotype specified."
                        {:invalid-target-biotype new-biotype})))
      (let [[src tgt] (map #(d/entity db [:gene/id %]) [src-id target-id])
            [src-cgc-name tgt-cgc-name] (map :gene/cgc-name [src tgt])]
        (when (every? :gene/cgc-name [src-cgc-name tgt-cgc-name])
          (throw (ex-info
                  (format (str "Both genes have a GCG name,"
                               "correct course of action to be "
                               "determined by the GCG admin"))
                  {:src-cgc-name src-cgc-name
                   :target-cgc-name tgt-cgc-name})))

        (when (and src-cgc-name (not tgt-cgc-name))
          ;; TODO: use logging instead of println
          (println (format (str "Gene to be merged has a CGC name " 
                                "and should probably be reatined. ")
                           {:src-cgc ("gene/cgc-name" src)})))

        ;; generate cas entries for all attributes being eaten
        ;; TODO: can provenance be supplied outside this context or is too complex?
        ;;       pattern used so far is for provenance to be passed in outside of a
        ;;       the context of a tx-fn. (i.e from the ring handler)
        (let [target-eid [:gene/id target-id]
              src-seq-name (:gene/sequence-name src)
              existing-bt [:biotype/id (:gene/biotype tgt)]
              txes [[:db.fn/cas target-eid :gene/sequence-name nil src-seq-name] 
                    [:db/retract target-eid :gene/biotype existing-bt new-biotype]
                    ;; TBD : new-biotype -> lookup-ref
                    [:ad/add [:gene/id target-id new-biotype]
                     [:db.fn/retractEntity [:gene/id src-id]]]]]
              (if tgt-cgc-name
                txes
                (cons [:db.fn/cas target-eid :gene/cgc-name nil src-cgc-name]
                      txes)))))))

(defn responses-map
  [success-code success-spec]
  (let [err-codes (range 400 501)
        default (->> {:schema "owsc/error-response"}
                     (repeat (count err-codes))
                     (interleave err-codes)
                     (apply sorted-map))
        response-map (assoc default
                            success-code
                            {:schema success-spec})]
    response-map))

(def routes
  (sweet/routes
   (sweet/context "/gene/" []
    :tags ["gene"]
     (sweet/resource
      {:get
       {:summary "Testing auth session."
        :x-name ::read-all
        :handler (fn [request]
                   (let [response (-> (resp/ok {:message "Hello World!"})
                                      (assoc-in [:session :identity]
                                                (if (:identity request)
                                                  (:identity request))))]
                     response))}
       :post
       {:summary "Create new names for a gene (cloned or un-cloned)"
        :x-name ::new
        :parameters {:body {:new ::owsg/new}}
        :responses {201 {:schema {:created ::owsg/created}}
                    400 {:schema  ::owsc/error-response}}
        :handler new-names}}))
   (sweet/context "/gene/:id" [id]
     :tags ["gene"]
     (sweet/resource
      {:get
       {:summary "Test a single Gene"
        :x-name ::read-one
        :handler (fn [request]
                   (http-response/ok {:message "testing"}))}
       :put
       {:summary "Add new names to an existing gene"
        :x-name ::update
        :parameters {:body {:add ::owsg/update}}
        :responses {200 {:schema {:updated ::owsg/updated}}
                    400 {:schema {:errors ::owsc/error-response}}}
        :handler (fn [request]
                   (update-names request id))}}))))
