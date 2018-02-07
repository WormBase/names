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
   [org.wormbase.specs.provenance :as owsp]
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

(defmulti resolve-ref
  (fn [kw db data]
    (kw data)))

(defmethod resolve-ref :gene/species [_ db data]
  (let [species-ident (-> data :gene/species vec)
        species-entid (d/entid db species-ident)]
    (println)
    (println "!!!!! >> entid for" species-ident "was" species-entid)
    (println)
    species-entid))

(defmethod resolve-ref :gene/biotype [_ db data]
  (when (contains? :gene/biotype data)
    (d/entid db (-> data :gene/biotype :biotype/id))))

(defn select-keys-with-ns [data key-ns]
  (into {} (filter #(= (namespace (key %)) key-ns)) data))

(defn assoc-provenence [request payload]
  (let [prov (select-keys-with-ns payload "provenance")
        who (:provenance/who prov)

        ;; TODO (!important): determine :provenance/how via credentials
        ;; used, not user-agent string.
        user-agent (get-in request [:headers "user-agent"])
        is-client-script? (and user-agent
                               (str/includes? user-agent "script"))]
    (if-not (:db request)
      (throw (ex-info "DB was nil!" {})))
    (cond-> prov
      (:user/email who)
      (assoc :provenance/who (:db/id
                              (d/entity (:db request)
                                        [:user/email (:user/email who)])))

      (not (:provenance/why prov))
      (assoc :provenance/why "No reason given")

      (not (:provenance/when prov))
      (assoc :provenance/when (jt/to-java-date (jt/instant)))

      
      is-client-script?
      (assoc :provenance/how [:agent/id :agent/script])

      (not is-client-script?)
      (assoc :provenance/how [:agent/id :agent/web-form]))))

(defn new-names [request]
  ;; TODO: "who" needs to come from auth problably should ditch
  ;;      WBPerson ids in favour of emails -in sanger-nameserver data,
  ;;      "log_who" is always a staff member. So instead of :person/id
  ;;      it should be :staff/id or :wbperson/id etc.
  (let [data (some-> request :body-params :new)
        db (:db request)
        name-record (select-keys-with-ns data "gene")
        prov (assoc (assoc-provenence request data) :db/id "datomic.tx")
        spec ::owsg/new
        txes [[:wormbase.tx-fns/new-name "gene" name-record spec]
              prov]]
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
        (let [data (some-> request :body-params)
              name-record (select-keys-with-ns data "gene")
              prov (assoc-provenence request data)
              txes [[:wormbase.tx-fns/update-name
                     lur
                     name-record
                     ::owsg/update]
                    prov]
              tx-result @(d/transact conn txes)]
          (if (:db-after tx-result)
            (resp/ok {:updated {:gene/id gene-id}})
            (resp/not-found (format "Gene '%s' does not exist" gene-id)))))))

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
               [_ :db/valueType ?e]
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
        :parameters {:body ::owsg/update}
        :responses {200 {:schema {:updated ::owsg/updated}}
                    400 {:schema {:errors ::owsc/error-response}}}
        :handler (fn [request]
                   (update-names request id))}}))))
