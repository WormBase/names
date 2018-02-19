(ns org.wormbase.names.gene
  (:require
   [clojure.string :as str]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [java-time :as jt]
   [ring.util.http-response :as resp]
   [org.wormbase.names.auth :as own-auth]
   [org.wormbase.names.util :as ownu]
   [org.wormbase.names.util :refer [namespace-keywords]]
   [org.wormbase.specs.common :as owsc]
   [org.wormbase.specs.gene :as owsg]
   [org.wormbase.specs.provenance :as owsp]
   [org.wormbase.specs.auth :as oswa]
   [ring.util.http-response :as http-response]))

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
    species-entid))

(defmethod resolve-ref :gene/biotype [_ db data]
  (when (contains? :gene/biotype data)
    (d/entid db (:gene/biotype data))))

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

(defn new [request]
  ;; TODO: "who" needs to come from auth problably should ditch
  ;;      WBPerson ids in favour of emails -in sanger-nameserver data,
  ;;      "log_who" is always a staff member. So instead of :person/id
  ;;      it should be :staff/id or :wbperson/id etc.
  (let [data (some-> request :body-params :new)
        name-record (select-keys-with-ns data "gene")
        prov (-> request
                 (assoc-provenence data)
                 (assoc :db/id "datomic.tx"))
        spec ::owsg/new
        txes [[:wormbase.tx-fns/new "gene" name-record spec]
              prov]]
    (let [tx-result @(d/transact (:conn request) txes)
          db (:db-after tx-result)
          new-id (extract-id tx-result)
          ent (d/entity db [:gene/id new-id])
          emap (ownu/entity->map ent)
          result {:created emap}]
      (resp/created "/gene/" result))))

(defn update-names [request gene-id]
  (let [conn (:conn request)
        db (:db request)
        lur [:gene/id gene-id]
        entity (d/entity db lur)]
      (when entity
        (let [data (some-> request :body-params)
              name-record (select-keys-with-ns data "gene")
              prov (assoc-provenence request data)
              txes [[:wormbase.tx-fns/update-name
                     lur
                     name-record
                     ::owsg/update]
                    prov]
              tx-result @(d/transact conn txes)
              ent (d/entity db lur)]
          (if (:db-after tx-result)
            (resp/ok {:updated (ownu/entity->map ent)})
            (resp/not-found (format "Gene '%s' does not exist" gene-id)))))))

(defn merge-from [request source-id target-id]
  (let [conn (:conn request)
        data (:body-params request)
        target-biotype (:gene/biotype data)
        prov (-> (assoc-provenence request data)
                 (assoc :provenance/merged-from [:gene/id source-id]))
        tx-result @(d/transact conn
                               [[:wormbase.tx-fns/merge-genes
                                 source-id
                                 target-id
                                 :gene/id
                                 target-biotype]
                                prov])]
    (if-let [db (:db-after tx-result)]
      (let [ent (d/entity (:db-after tx-result) [:gene/id target-id])]
        (resp/ok {:updated (ownu/entity->map ent)}))
      (resp/bad-request {:reason tx-result}))))

(defn split-into [request id]
  (let [conn (:conn request)
        params (some-> request :body-params)
        new-biotype (:gene/biotype params)
        [prod-seq-name prod-biotype] (map
                                      #(get-in params [:product %])
                                      [:gene/sequence-name
                                       :gene/biotype])
        tx-result @(d/transact conn [[:wormbase.tx-fns/split-gene
                                      id
                                      new-biotype
                                      prod-seq-name
                                      prod-biotype]])]
    (let [db (:db-after tx-result)
          [ent product] (map #(d/entity db [:gene/id %]) [])]
      nil)))

(defn idents-by-ns [db ns-name]
  (sort (d/q '[:find [?ident ...]
               :in $ ?ns-name
               :where
               [?e :db/ident ?ident]
               [_ :db/valueType ?e]
               [(namespace ?ident) ?ns]
               [(= ?ns ?ns-name)]]
             db ns-name)))

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
        :handler new}}))
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
                   (update-names request id))}})
     (sweet/context "/merge/:another-id" [another-id]
       (sweet/resource
        {:post
         {:summary "Merge one gene with another."
          :x-name ::merge
          :path-params [id :gene/id
                        another-id :gene/id]
          :parameters {:body {:gene/biotype :gene/biotype}}
          :responses {200 {:schema {:updated ::owsg/updated}}
                      400 {:schema {:errors ::owsc/error-response}}
                      409 {:schema {:conflict ::owsc/conflict-response}}}
          :handler
          (fn [request]
            (merge-from request another-id id))}}))
     (sweet/context "/split/:id" []
       (sweet/resource
        {:post
         {:summary "Split a gene."
          :x-name ::split
          :path-params [id :- :gene/id]
          :parameters {:body ::owsg/split}
          :responses {201 {:schema {:updated ::owsg/updated}}
                      400 {:schema {:errors ::owsc/error-response}}}
          :handler
          (fn [request]
            (split-into request id))}})))))
  
