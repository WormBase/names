(ns wormbase.names.gene
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [java-time :as jt]
   [wormbase.db :as wdb]
   [wormbase.names.agent :as wn-agent]
   [wormbase.names.auth :as wna]
   [wormbase.names.entity :as wne]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.common :as wsc]
   [wormbase.specs.gene :as wsg]
   [ring.util.http-response :as http-response]
   [spec-tools.core :as stc]))

(def identify (partial wne/identify ::wsg/identifier))

(defn validate-names [request data]
  (let [db (:db request)
        species-lur (some-> data :gene/species vec first)
        species-ent (d/entity db species-lur)]
    (if (empty? data)
      (throw (ex-info "No names to validate (empty data)"
                      {:type :user/validation-error})))
    (if-not species-ent
      (throw (ex-info "Invalid species specified"
                      {:invalid-species species-lur
                       :type :user/validation-error})))
    (let [patterns ((juxt :species/cgc-name-pattern
                          :species/sequence-name-pattern) species-ent)
          regexps (map re-pattern patterns)
          name-idents [:gene/cgc-name :gene/sequence-name]]
      (doseq [[regexp name-ident] (partition 2 (interleave regexps name-idents))]
        (when-let [gname (name-ident data)]
          (when-not (re-matches regexp gname)
            (throw (ex-info "Invalid name"
                            {:type :user/validation-error
                             :invalid {:name  gname :ident name-ident}})))))))
  data)

(def name-matching-rules
  '[[(matches-name ?attr ?pattern ?name ?eid ?attr)
     [(re-seq ?pattern ?name)]
     [?a :db/ident ?attr]
     [?eid ?a ?name]]
    [(gene-name ?pattern ?name ?eid ?attr)
     (matches-name :gene/cgc-name ?pattern ?name ?eid ?attr)]
    [(gene-name ?pattern ?name ?eid ?attr)
     (matches-name :gene/sequence-name ?pattern ?name ?eid ?attr)]
    [(gene-name ?pattern ?name ?eid ?attr)
     (matches-name :gene/id ?pattern ?name ?eid ?attr)]])

(defn find-gene
  "Perform a prefix search against names in the DB.
  Match any unique gene identifier (cgc, sequence names or id)."
  [request]
  (when-let [pattern (some-> request :query-params :pattern str/trim)]
    (if (s/valid? ::wsg/find-term pattern)
      (let [db (:db request)
            term (stc/conform ::wsg/find-term pattern)
            q-result (d/q '[:find ?gid ?attr ?name
                            :in $ % ?term
                            :where
                            (gene-name ?term ?name ?eid ?attr)
                            [?eid :gene/id ?gid]]
                          db
                          name-matching-rules
                          (re-pattern (str "^" term)))
            res {:matches (or (some->> q-result
                                       (map (fn matched [[gid attr name*]]
                                              (array-map :gene/id gid attr name*)))
                                       (vec))
                              [])}]
        (http-response/ok res))
      (http-response/bad-request {:message "Invalid find term"
                                  :value pattern
                                  :problems (str (s/explain-data ::wsg/find-term
                                                                 pattern))}))))

(defn- transform-result
  "Removes datomic internal keys from a pull-result map."
  [pull-result]
  (reduce-kv (fn unravel-enums [m k v]
               (assoc m k (if (and (map? v) (:db/ident v))
                            (:db/ident v)
                            v)))
             (empty pull-result)
             (dissoc pull-result :db/id)))

(defn about-gene [request identifier]
  (when (s/valid? ::wsg/identifier identifier)
    (let [db (:db request)
          [lur ent] (identify request identifier)
          info-expr '[*
                      {:gene/biotype [[:db/ident]]
                       :gene/species [[:species/id][:species/latin-name]]
                       :gene/status [[:db/ident]]}]
          data (d/pull db info-expr lur)]
      (http-response/ok (transform-result data)))))

(defn new-unnamed-gene [request payload]
  (let [prov (wnp/assoc-provenence request payload :event/new-gene)
        data (wnu/select-keys-with-ns payload "gene")
        spec ::wsg/new-unnamed
        cdata (if (s/valid? spec data)
                (s/conform spec data)
                (let [problems (s/explain-data spec data)]
                  (throw (ex-info "Invalid data"
                                  {:problems problems
                                   :type ::validation-error
                                   :data data}))))
        tx-data [[:wormbase.tx-fns/new-unnamed-gene cdata] prov]]
    @(d/transact-async (:conn request) tx-data)))

(defn new-gene [request & {:keys [mint-new-id?]
                           :or {mint-new-id? true}}]
  (let [payload (:body-params request)
        data (wnu/select-keys-with-ns payload "gene")
        spec ::wsg/new
        [_ cdata] (let [conformed (stc/conform spec (validate-names request data))]
                    (if (= ::s/invalid conformed)
                      (let [problems (s/explain-data spec data)]
                        (throw (ex-info "Not valid according to spec."
                                        {:problems (str problems)
                                         :type ::validation-error
                                         :data data})))
                      conformed))
        prov (wnp/assoc-provenence request payload :event/new-gene)
        tx-data [[:wormbase.tx-fns/new-gene cdata mint-new-id?] prov]
        tx-result @(d/transact-async (:conn request) tx-data)
        db (:db-after tx-result)
        new-id (wdb/extract-id tx-result :gene/id)
        ent (d/entity db [:gene/id new-id])
        emap (wnu/entity->map ent)
        result {:created emap}]
      (http-response/created "/gene/" result)))

(defn update-gene [request identifier]
  (let [{db :db conn :conn} request
        [lur entity] (identify request identifier)]
    (when entity
      (let [payload (some-> request :body-params)
            spec ::wsg/update
            data (wnu/select-keys-with-ns payload "gene")]
        (if (s/valid? ::wsg/update data)
          (let [cdata (stc/conform spec (validate-names request data))
                prov (wnp/assoc-provenence request payload :event/update-gene)
                txes [[:wormbase.tx-fns/update-gene lur cdata]
                      prov]
                tx-result @(d/transact-async conn txes)]
            (if-let [db-after (:db-after tx-result)]
              (let [ent (d/entity db-after lur)]
                (http-response/ok {:updated (wnu/entity->map ent)}))
              (http-response/not-found
               (format "Gene '%s' does not exist" (last lur)))))
          (throw (ex-info "Not valid according to spec."
                          {:problems (str (s/explain-data spec data))
                           :type ::validation-error
                           :data data})))))))

(defn- validate-merge-request
  [request into-gene-id from-gene-id into-biotype]
  (let [db (:db request)
        [[into-lur into-gene]
         [from-lur from-gene]] (map (partial identify request)
                                    [into-gene-id from-gene-id])]
    (when (some nil? [into-gene from-gene])
      (throw (ex-info "Missing gene in database, cannot merge."
                      {:missing (if-not into
                                  into-gene-id
                                  from-gene-id)
                       :type :wormbase.db/missing})))
    (when (= from-gene-id into-gene-id)
      (throw (ex-info "Source and into ids cannot be the same!"
                      {:from-id from-gene-id
                       :into-id into-gene-id
                       :type ::validation-error})))
    (when-not (and (s/valid? :gene/biotype into-biotype)
                   (wdb/ident-exists? db into-biotype))
      (throw (ex-info "Invalid biotype"
                      {:problems (str (s/explain-data
                                       :gene/biotype
                                       into-biotype))
                       :type ::validation-error})))
    (when (reduce not=
                  (map :gene/species [from-gene into-gene]))
      (throw (ex-info
              "Refusing to merge: genes have differing species"
              {:from {:gen/species (-> from-gene :gene/species :species/id)
                      :gene/id from-gene-id}
               :into {:species (-> into :gene/species :species/id)
                      :gene/id into-gene-id}})))
    (when (:gene/cgc-name from-gene)
      (throw (ex-info (str "Gene to be killed has a CGC name,"
                           "refusing to merge.")
                      {:from-id from-gene-id
                       :from-cgc-name (:gene/cgc-name from-gene)
                       :type :wormbase.db/conflict})))
    [[into-lur into-gene] [from-lur from-gene]]))

(defn merge-genes [request into-id from-id]
  (let [conn (:conn request)
        data (:body-params request)
        into-biotype (:gene/biotype data)
        [[into-lur into] [from-lur form]] (validate-merge-request
                                           request
                                           into-id
                                           from-id
                                           into-biotype)
        prov (-> request
                 (wnp/assoc-provenence data :event/merge-genes)
                 (assoc :provenance/merged-from from-lur)
                 (assoc :provenance/merged-into into-lur))
        tx-result @(d/transact-async
                    conn
                    [[:wormbase.tx-fns/merge-genes from-id into-id into-biotype]
                     prov])]
      (if-let [db (:db-after tx-result (:tx-data tx-result))]
        (let [[from into] (map #(d/entity db [:gene/id %])
                               [from-id into-id])]
          (http-response/ok {:updated (wnu/entity->map into)
                             :statuses
                             {from-id (:gene/status from)
                              into-id (:gene/status into)}}))
        (http-response/bad-request {:message "Invalid transaction"}))))

(defn undo-merge-gene [request from-id into-id]
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
          compensating-txes (wdb/invert-tx (d/log conn) tx prov)
          tx-result @(d/transact-async conn compensating-txes)]
      (http-response/ok {:live into-id :dead from-id}))
    (http-response/not-found {:message "No transaction to undo"})))

(defn split-gene [request id]
  (let [conn (:conn request)
        db (d/db conn)
        data (some-> request :body-params)
        spec ::wsg/split]
    (if-let [[lur existing-gene] (identify request id)]
      (let [cdata (stc/conform spec data)
            {biotype :gene/biotype product :product} cdata
            {p-seq-name :gene/sequence-name
             p-biotype :gene/biotype} product
            p-seq-name (get-in cdata [:product :gene/sequence-name])
            prov-from (-> request
                          (wnp/assoc-provenence cdata :event/split-gene)
                          (assoc :provenance/split-into p-seq-name)
                          (assoc :provenance/split-from [:gene/id id]))
            species (-> existing-gene :gene/species :species/id)
            mint-new-id? true
            tx-result @(d/transact-async
                        conn
                        [[:wormbase.tx-fns/split-gene id cdata mint-new-id?]
                         prov-from])
            db (:db-after tx-result)
            new-gene-id (-> db
                            (d/entity [:gene/sequence-name p-seq-name])
                            :gene/id)
            [ent product] (map #(d/entity db [:gene/id %]) [id new-gene-id])
            updated (-> (wnu/entity->map ent)
                        (select-keys [:gene/id
                                      :gene/biotype
                                      :gene/sequence-name])
                        (assoc :gene/species {:species/id species}))
            created (wnu/entity->map product)]
        (http-response/created (str "/gene/" new-gene-id)
                               {:updated updated :created created}))
      (http-response/not-found {:gene/id id :message "Gene not found"}))))

(defn ressurect-gene [request identifier]
  (if (s/valid? ::wsg/identifier identifier)
    (let [[ident val] (s/conform ::wsg/identifier identifier)
          tx-res @(d/transact-async (:conn request)
                                    [[:db.fn/cas
                                      [ident val]
                                      :gene/status
                                      :gene.status/dead
                                      :gene.status/live]])]
      (http-response/ok {:gene/id identifier}))))

(defn- invert-split-tx [db e a v tx added?]
  (cond
    (= (d/ident db a) :gene/status)
    [:db.fn/cas e a v (d/entid db :gene.status/dead)]

    (= (d/ident db a) :gene/id)
    [:db/add e a v]

    :default
    [(if added? :db/retract :db/add) e a v]))

(defn undo-split-gene [request from-id into-id]
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
          compensating-txes (wdb/invert-tx (d/log conn)
                                            tx
                                            prov
                                            fact-mapper)
          tx-result @(d/transact-async conn compensating-txes)]
      (http-response/ok {:live from-id :dead into-id}))
    (http-response/not-found {:message "No transaction to undo"})))

(defn kill-gene [request id]
  (let [[lur ent] (identify request id)
        data (assoc-in (apply array-map lur)
                       [:gene/species :species/id]
                       (-> ent :gene/species :species/id))]
    (validate-names request data)
    (when (= (:gene/status ent) :gene.status/dead)
      (throw (ex-info "Cannot kill dead gene"
                      {:type ::wdb/conflict
                       :lookup-ref lur})))
    (when ent
      (let [payload (some->> request :body-params)
            prov (wnp/assoc-provenence request payload :event/kill-gene)
            tx-result @(d/transact-async
                        (:conn request)
                        [[:wormbase.tx-fns/kill-gene lur] prov])]
        (when tx-result
          (http-response/ok {:killed id}))))))

(def default-responses
  {200 {:schema {:updated ::wsg/updated}}
   400 {:schema {:errors ::wsc/error-response}}
   409 {:schema {:conflict ::wsc/conflict-response}}})

(def routes
  (sweet/routes
   (sweet/context "/gene/" []
     :tags ["gene"]
     (sweet/resource
      {:get
       {:summary "Find genes by any name."
        :responses (assoc default-responses
                          200
                          {:schema ::wsg/find-result})
        :parameters {:query-params ::wsg/find-request}
        :x-name ::find-gene
        :handler (fn find-by-any-name [request]
                   (find-gene request))}
       :post
       {:summary "Create new names for a gene (cloned or un-cloned)"
        :middleware [wna/restrict-to-authenticated]
        :x-name ::new-gene
        :parameters {:body-params ::wsg/new}
        :responses {201 {:schema {:created ::wsg/created}}
                    400 {:schema ::wsc/error-response}}
        :handler new-gene}}))
   (sweet/context "/gene/:identifier" [identifier]
     :tags ["gene"]
     (sweet/resource
      {:delete
       {:summary "Kill a gene"
        :middleware [wna/restrict-to-authenticated]
        :x-name ::kill-gene
        :parameters {:body-params ::wsg/kill}
        :responses (assoc default-responses 200 {:schema ::wsg/kill})
        :path-params [identifier :- ::wsg/identifier]
        :handler (fn [request]
                   (kill-gene request identifier))}
       :get
       {:summary "Information about a given gene."
        :x-name ::about-gene
        :path-params [identifier :- ::wsg/identifier]
        :responses (assoc default-responses 200 {:schema ::wsg/info})
        :handler (fn [request]
                   (about-gene request identifier))}
       :put
       {:summary "Add new names to an existing gene"
        :middleware [wna/restrict-to-authenticated]
        :x-name ::update-gene
        :parameters {:body-params ::wsg/update}
        :path-params [identifier :- ::wsg/identifier]
        :responses (dissoc default-responses 409)
        :handler (fn [request]
                   (update-gene request identifier))}})
     (sweet/context "/merge/:from-identifier" [from-identifier]
       (sweet/resource
        {:post
         {:summary "Merge one gene with another."
          :middleware [wna/restrict-to-authenticated]
          :x-name ::merge-gene
          :path-params [identifier ::wsg/identifier
                        from-identifier ::wsg/identifier]
          :parameters {:body-params {:gene/biotype :gene/biotype}}
          :responses default-responses
          :handler
          (fn [request]
            (merge-genes request identifier from-identifier))}
         :delete
         {:summary "Undo a merge operation."
          :middleware [wna/restrict-to-authenticated]
          :x-name ::undo-merge-gene
          :path-params [identifier ::wsg/identifier
                        from-identifier ::wsg/identifier]
          :responses (assoc default-responses 200 {:schema ::wsg/undone})
          :handler (fn [request]
                     (undo-merge-gene request from-identifier identifier))}}))
     (sweet/context "/split" []
       (sweet/resource
        {:post
         {:summary "Split a gene."
          :middleware [wna/restrict-to-authenticated]
          :x-name ::split-genes
          :path-params [identifier :- ::wsg/identifier]
          :parameters {:body-params ::wsg/split}
          :responses (-> (dissoc default-responses 200)
                         (assoc 201 {:schema ::wsg/split-response}))
          :handler
          (fn [request]
            (split-gene request identifier))}}))
     (sweet/context "/split/:into-identifier" [into-identifier]
       (sweet/resource
        {:delete
         {:summary "Undo a split gene operation."
          :middleware [wna/restrict-to-authenticated]
          :x-name ::undo-split-gene
          :path-params [identifier :- ::wsg/identifier
                        into-identifier :- ::wsg/identifier]
          :responses (assoc default-responses
                            200
                            {:schema ::wsg/undone})
          :handler (fn [request]
                     (undo-split-gene request
                                      identifier
                                      into-identifier))}}))
     (sweet/context "/resurrect" []
       (sweet/resource
        {:post
         {:summary "Ressurect a gene."
          :middleware [wna/restrict-to-authenticated]
          :x-name ::ressurect-gene
          :responses default-responses
          :handler (fn [request]
                     (ressurect-gene request identifier))}})))))
