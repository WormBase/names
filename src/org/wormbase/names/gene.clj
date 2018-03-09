(ns org.wormbase.names.gene
  (:require
   [clojure.spec.alpha :as s]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [java-time :as jt]
   [org.wormbase.db :as owdb]
   [org.wormbase.names.agent :as own-agent]
   [org.wormbase.names.util :as ownu]
   [org.wormbase.specs.common :as owsc]
   [org.wormbase.specs.gene :as owsg]
   [ring.util.http-response :as http-response])
  (:refer-clojure :exclude [merge]))

(defn- extract-id [tx-result]
  (some->> (:tx-data tx-result)
           (map :e)
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
  (let [id-token (:identity request)
        prov (or (select-keys-with-ns payload "provenance") {})
        email (or (some-> prov :provenance/who :person/email)
                  (:email id-token))
        who (:db/id (d/entity (:db request) [:person/email email]))
        when (get prov :provenance/when (jt/to-java-date (jt/instant)))
        how (own-agent/identify id-token)
        why (:provenance/why prov)]
    (cond-> prov
      why
      (assoc :provenance/why why)
      :always
      (assoc :db/id "datomic.tx"
             :provenance/who who
             :provenance/when when
             :provenance/how how))))

(defn new-record [request]
  (let [data (some-> request :body-params :new)
        name-record (select-keys-with-ns data "gene")
        tempid (-> data ((juxt :gene/sequence-name :gene/cgc-name)) first)
        prov (-> request
                 (assoc-provenence data)
                 (assoc :db/id tempid))
        spec ::owsg/new
        txes [[:wormbase.tx-fns/new "gene" name-record spec]
              prov]]
    (let [tx-result @(d/transact-async (:conn request) txes)
          db (:db-after tx-result)
          new-id (extract-id tx-result)
          ent (d/entity db [:gene/id new-id])
          emap (ownu/entity->map ent)
          result {:created emap}]
      (http-response/created "/gene/" result))))

(defn update-record [request gene-id]
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
            (http-response/ok {:updated (ownu/entity->map ent)})
            (http-response/not-found (format "Gene '%s' does not exist" gene-id)))))))

(defn merge [request into-id from-id]
  (let [conn (:conn request)
        data (:body-params request)
        into-biotype (:gene/biotype data)
        prov (-> request
                 (assoc-provenence data)
                 (assoc :provenance/merged-from [:gene/id from-id])
                 (assoc :provenance/merged-into [:gene/id into-id]))
        tx-result @(d/transact-async
                    conn
                    [[:wormbase.tx-fns/merge-genes
                      from-id
                      into-id
                      :gene/id
                      into-biotype]
                     prov])]
    (if-let [db (:db-after tx-result (:tx-data tx-result))]
      (let [[from into] (map #(d/entity db [:gene/id %])
                             [from-id into-id])]
        (http-response/ok {:updated (ownu/entity->map into)
                  :statuses
                  {from-id (:gene/status from)
                   into-id (:gene/status into)}}))
      (http-response/bad-request {:message "Invalid transaction"}))))

(defn undo-merge [request from-id into-id]
  (if-let [tx (d/q '[:find ?tx .
                     :in $ ?from ?into
                     :where
                     [?from :gene/status :gene.status/dead ?tx]
                     [?tx :provenance/merged-from ?from]
                     [?tx :provenance/merged-into ?into]]
                   (-> request :db d/history)
                   [:gene/id from-id]
                   [:gene/id into-id])]
    (let [conn (:conn request)
          prov {:db/id "datomic.tx"
                :provenance/compensates tx
                :provenance/why "Undoing merge"}
          compensating-txes (owdb/invert-tx (d/log conn) tx prov)
          tx-result @(d/transact-async conn compensating-txes)]
      (http-response/ok {:live into-id :dead from-id}))
    (http-response/not-found {:message "No transaction to undo"})))

(defn split [request id]
  (let [conn (:conn request)
        db (d/db conn)
        data (some-> request :body-params)]
    (if (s/valid? ::owsg/split data)
      (if-let [existing-gene (d/entity db [:gene/id id])]
        (let [{biotype :gene/biotype product :product} data
              {p-seq-name :gene/sequence-name
               p-biotype :gene/biotype} product
              p-seq-name (get-in data [:product :gene/sequence-name])
              prov-from (-> request
                            (assoc-provenence data)
                            (assoc :provenance/split-into p-seq-name)
                            (assoc :provenance/split-from [:gene/id id]))
              species (-> existing-gene :gene/species :species/id)
              tx-result @(d/transact-async
                          conn
                          [[:wormbase.tx-fns/split-gene
                            id
                            data
                            ::owsg/new]
                           prov-from])
              db (:db-after tx-result)
              new-gene-id (-> db
                              (d/entity [:gene/sequence-name p-seq-name])
                              :gene/id)
              [ent product] (map #(d/entity db [:gene/id %]) [id new-gene-id])
              updated (-> (ownu/entity->map ent)
                          (select-keys [:gene/id
                                        :gene/biotype
                                        :gene/sequence-name])
                          (assoc :gene/species {:species/id species}))
              created (ownu/entity->map product)]
          (http-response/created (str "/gene/" new-gene-id)
                                 {:updated updated :created created}))
        (http-response/not-found {:gene/id id :message "Gene not found"}))
      (let [spec-report (s/explain-data ::owsg/split data)]
        ;; TODO: finer grained error message here would be good.
        ;;       e.g (phrase-first {} ::owsg/split spec-report) ->
        ;;           "biotype missing in split product"
        (throw (ex-info "Invalid split request"
                        {:type :user/validation-error
                         :problems spec-report}))))))

(defn- invert-split-tx [db e a v tx added?]
  (cond
    (= (d/ident db a) :gene/status)
    [:db.fn/cas e a v (d/entid db :gene.status/dead)]

    (= (d/ident db a) :gene/id)
    [:db/add e a v]

    :default
    [(if added? :db/retract :db/add) e a v]))

(defn undo-split [request from-id into-id]
  (if-let [tx (d/q '[:find ?tx .
                     :in $ ?from ?into
                     :where
                     [?into :gene/id]
                     [?tx :provenance/split-into ?into]
                     [?tx :provenance/split-from ?from]]
                   (-> request :db d/history)
                   [:gene/id from-id]
                   [:gene/id into-id])]
    (let [conn (:conn request)
          prov {:db/id "datomic.tx"
                :provenance/compensates tx
                :provenance/why "Undoing split"}
          fact-mapper (partial invert-split-tx (d/db conn))
          compensating-txes (owdb/invert-tx (d/log conn)
                                            tx
                                            prov
                                            fact-mapper)
          tx-result @(d/transact-async conn compensating-txes)]
      (http-response/ok {:live from-id :dead into-id}))
    (http-response/not-found {:message "No transaction to undo"})))

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

(def default-responses
  {200 {:schema {:updated ::owsg/updated}}
   400 {:schema {:errors ::owsc/error-response}}
   409 {:schema {:conflict ::owsc/conflict-response}}})

(def routes
  (sweet/routes
   (sweet/context "/gene/" []
    :tags ["gene"]
    (sweet/resource
      {:get
       {:summary "Testing auth session."
        :x-name ::read-all
        :handler (fn [request]
                   (let [response (-> (http-response/ok
                                       {:message "Hello World!"})
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
        :handler new-record}}))
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
        :responses (dissoc default-responses 409)
        :handler (fn [request]
                   (update-record request id))}})
     (sweet/context "/merge-from/:from-id" [from-id]
       (sweet/resource
        {:post
         {:summary "Merge one gene with another."
          :x-name ::merge
          :path-params [id :gene/id
                        from-id :gene/id]
          :parameters {:body {:gene/biotype :gene/biotype}}
          :responses default-responses
          :handler
          (fn [request]
            (merge request id from-id))}
         :delete
         {:summary "Undo a merge operation."
          :x-name ::undo-merge
          :path-params [id :gene/id
                        from-id :gene/id]
          :responses (assoc default-responses 200 {:schema ::owsg/undone})
          :handler (fn [request]
                     (undo-merge request from-id id))}}))
     (sweet/context "/split" []
       (sweet/resource
        {:post
         {:summary "Split a gene."
          :x-name ::split
          :path-params [id :- :gene/id]
          :parameters {:body ::owsg/split}
          :responses (-> (dissoc default-responses 200)
                         (assoc 201 {:schema ::owsg/split-response}))
          :handler
          (fn [request]
            (split request id))}}))
     (sweet/context "/split/:into-id" [into-id]
       (sweet/resource
        {:delete
         {:summary "Undo a split gene operation."
          :x-name ::undo-split
          :path-params [id :- :gene/id
                        into-id :- :gene/id]
          :responses (assoc default-responses
                            200
                            {:schema ::owsg/undone})
          :handler (fn [request]
                     (undo-split request id into-id))}})))))
