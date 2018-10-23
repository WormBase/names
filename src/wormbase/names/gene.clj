(ns wormbase.names.gene
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
                                    conflict conflict!
                                    created
                                    not-found not-found!
                                    precondition-failed precondition-failed!]]
   [spec-tools.core :as stc]
   [wormbase.db :as wdb]
   [wormbase.util :as wu]
   [wormbase.names.auth :as wna]
   [wormbase.names.entity :as wne]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.specs.common :as wsc]
   [wormbase.specs.gene :as wsg]))

(def identify (partial wne/identify ::wsg/identifier))

(defn entity-must-exist!
  "Middlewre for ensuring an entity exists in the database.

  Calls the handler for a route iif a entity exists, else returns a
  not-found response."
  [request & identifier]
  (doseq [identifier identifier]
    (let [[_ ent] (identify request identifier)]
      (if-not ent
        (not-found!
         {:message (str "Gene with identifier "
                        identifier
                        "does not exist")})))))

(defn validate-names
  ([request data & {:keys [allow-blank-cgc-name?]
                    :or {allow-blank-cgc-name? true}}]
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
            (when-not (and allow-blank-cgc-name?
                           (empty? (get data gname))
                           (= gname :gene/cgc-name))
              (throw (ex-info "Invalid name"
                              {:type :user/validation-error
                               :data {:problems {:invalid {:name gname :ident name-ident}}}}))))))
      data)))
  ([request data]
   (validate-names request data :allow-blank-cgc-name? false)))

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
    (let [db (:db request)
          term (stc/conform ::wsg/find-term pattern)
          q-result (d/q '[:find ?gid ?cgc-name ?sequence-name
                          :in $ % ?term
                          :where
                          (gene-name ?term ?name ?eid ?attr)
                          [?eid :gene/id ?gid]
                          [(get-else $ ?eid :gene/cgc-name "") ?cgc-name]
                          [(get-else $ ?eid :gene/sequence-name "") ?sequence-name]]
                        db
                        name-matching-rules
                        (re-pattern (str "^" term)))
          res {:matches (or (some->> q-result
                                     (map (fn matched [[gid cgc-name seq-name]]
                                            (merge {:gene/id gid}
                                                   (when (s/valid? :gene/cgc-name cgc-name)
                                                     {:gene/cgc-name cgc-name})
                                                   (when (s/valid? :gene/sequence-name seq-name)
                                                     {:gene/sequence-name seq-name}))))
                                     (vec))
                            [])}]
      (ok res))))

(def provenance-pull-expr '[*
                            {:provenance/what [:db/ident]
                             :provenance/who [:person/email :person/name :person/id]
                             :provenance/how [:db/ident]
                             :provenance/split-from [:gene/id]
                             :provenance/split-into [:gene/id]
                             :provenance/merged-from [:gene/id]
                             :provenance/merged-into [:gene/id]}])

(def info-pull-expr '[* {:gene/biotype [[:db/ident]]
                         :gene/species [[:species/id][:species/latin-name]]
                         :gene/status [[:db/ident]]}])

(defn about-gene [request identifier]
  (entity-must-exist! request identifier)
  (let [db (:db request)
        [lur _] (identify request identifier)
        info (wdb/pull db info-pull-expr lur)
        prov (wnp/query-provenance db lur provenance-pull-expr)]
    (-> info (assoc :history prov) ok)))

(defn new-unnamed-gene [request payload]
  (let [prov (wnp/assoc-provenance request payload :event/new-gene)
        data (wnu/select-keys-with-ns payload "gene")
        spec ::wsg/new-unnamed
        cdata (if (s/valid? spec data)
                (s/conform spec data)
                (let [problems (expound-str spec data)]
                  (throw (ex-info "Invalid data"
                                  {:problems problems
                                   :type ::validation-error
                                   :data data}))))
        tx-data [[:wormbase.tx-fns/new-unnamed-gene cdata] prov]]
    @(d/transact-async (:conn request) tx-data)))

(defn conform-gene-data [request spec data]
  (let [conformed (stc/conform spec (validate-names request data))]
    (if (s/invalid? conformed)
      (let [problems (expound-str spec data)]
        (throw (ex-info "Not valid according to spec."
                        {:problems problems
                         :type ::validation-error
                         :data data})))
      conformed)))

(defn new-gene [request & {:keys [mint-new-id?]
                           :or {mint-new-id? true}}]
  (let [payload (:body-params request)
        data (wnu/select-keys-with-ns payload "gene")
        spec ::wsg/new
        [_ cdata] (conform-gene-data request spec data)
        prov (wnp/assoc-provenance request payload :event/new-gene)
        tx-data [[:wormbase.tx-fns/new-gene cdata mint-new-id?] prov]
        tx-result @(d/transact-async (:conn request) tx-data)
        db (:db-after tx-result)
        new-id (wdb/extract-id tx-result :gene/id)
        ent (d/entity db [:gene/id new-id])
        emap (wnu/entity->map ent)
        result {:created emap}]
    (created "/gene/" result)))

(defn resolve-refs-to-dbids
  "Resolve references in a data payload to database ids for compare on swap operations."
  [db data]
  (let [species-lur (-> data :gene/species vec first)
        species-entid (d/entid db species-lur)
        biotype-ident (get data :gene/biotype)
        biotype-entid (d/entid db biotype-ident)
        res (vec (merge data
                       (when biotype-entid
                         {:gene/biotype biotype-entid})
                       (when species-entid
                         {:gene/species species-entid})))]
    res))

(defn update-gene [request identifier]
  (entity-must-exist! request identifier)
  (let [{db :db conn :conn} request
        [lur entity] (identify request identifier)]
    (when entity
      (let [payload (some-> request :body-params)
            spec ::wsg/update
            data (wnu/select-keys-with-ns payload "gene")]
        (if (s/valid? ::wsg/update data)
          (let [cdata (->> (validate-names request data :allow-blank-cgc-name? true)
                           (conform-gene-data request spec)
                           (second)
                           (resolve-refs-to-dbids db))
                prov (wnp/assoc-provenance request payload :event/update-gene)
                txes [[:wormbase.tx-fns/update-gene lur cdata] prov]
                tx-result @(d/transact-async conn txes)]
            (if-let [db-after (:db-after tx-result)]
              (let [ent (d/entity db-after lur)]
                (ok {:updated (wnu/entity->map ent)}))
              (not-found
               (format "Gene '%s' does not exist" (last lur)))))
          (throw (ex-info "Not valid according to spec."
                          {:problems (expound-str spec data)
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
                      {:missing (if-not into-gene
                                  into-gene-id
                                  from-gene-id)
                       :type :wormbase.db/missing})))
    (when (= from-gene-id into-gene-id)
      (throw (ex-info "Source and into ids cannot be the same!"
                      {:from-id from-gene-id
                       :into-id into-gene-id
                       :type ::validation-error})))
    (cond
      (not (s/valid? :gene/biotype into-biotype))
      (throw (ex-info "Invalid biotype"
                      {:type ::validation-error
                       :problems (expound-str :gene/biotype into-biotype)}))

      (not (wdb/ident-exists? db into-biotype))
      (throw (ex-info "Biotype does not exist"
                      {:type ::wdb/missing
                       :problems "Biotype entity does not exist"})))
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
    (when-let [deads (filter #(= (:gene/status %) :gene.status/dead)
                             [from-gene into-gene])]
      (when (seq deads)
        (throw (ex-info "Both merge participants must be live"
                        {:type :wormbase.db/conflict
                         :dead-genes (map :gene/id deads)}))))
    [[into-lur into-gene] [from-lur from-gene]]))

(defn uncloned-merge-target? [target]
  (let [names ((juxt :gene/biotype :gene/sequence-name) target)]
    (every? nil? names)))

(defn merge-genes [request into-id from-id]
  (let [{conn :conn db :db data :body-params} request
        into-biotype (:gene/biotype data)
        [[into-lur into-g] [from-lur from-g]] (validate-merge-request
                                               request
                                               into-id
                                               from-id
                                               into-biotype)
        collate-cas-batch (partial d/invoke db :wormbase.tx-fns/collate-cas-batch db)
        from-seq-name (:gene/sequence-name from-g)
        assoc-merge-prov (partial wnp/assoc-provenance request data :event/merge-genes)
        from-prov (assoc (assoc-merge-prov) :provenance/merged-from from-lur)
        into-prov (assoc (assoc-merge-prov) :provenance/merged-into into-lur)
        into-uncloned? (uncloned-merge-target? into-g)
        [fid iid] (map :db/id [from-g into-g])
        from-txes (concat
                   (concat
                    (collate-cas-batch from-g {:gene/status :gene.status/dead})
                    (when into-uncloned?
                      [[:db/retract iid :gene/sequence-name from-seq-name]]))
                   [into-prov])
        into-txes (concat
                   (concat
                    (when into-uncloned?
                      [[:db.fn/cas iid :gene/sequence-name nil from-seq-name]])
                    (collate-cas-batch into-g {:gene/biotype into-biotype}))
                   [from-prov])
        tx-result (->> [from-txes into-txes]
                       (map (partial d/transact-async conn))
                       (map deref)
                       (last))]
      (if-let [db (:db-after tx-result (:tx-data tx-result))]
        (let [[from into] (map #(d/entity db [:gene/id %])
                               [from-id into-id])]
          (ok {:updated (wnu/entity->map into)
                             :statuses
                             {from-id (:gene/status from)
                              into-id (:gene/status into)}}))
        (bad-request {:message "Invalid transaction"}))))

(defn undo-merge-gene [request from-id into-id]
  (entity-must-exist! request from-id into-id)
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
          payload (get request :body-params {})
          prov (-> request
                   (wnp/assoc-provenance payload :event/undo-merge-genes)
                   (merge {:db/id "datomic.tx"
                           :provenance/compensates tx
                           :provenance/why "Undoing merge"}))
          compensating-txes (wdb/invert-tx (d/log conn) tx prov)
          tx-result @(d/transact-async conn compensating-txes)]
      (ok {:live into-id :dead from-id}))
    (not-found {:message "No transaction to undo"})))

(defn split-gene [request identifier]
  (entity-must-exist! request identifier)
  (let [conn (:conn request)
        db (d/db conn)
        data (get request :body-params {})
        spec ::wsg/split
        [lur from-gene] (identify request identifier)
        from-gene-status (:gene/status from-gene)]
    (when (wnu/not-live? from-gene-status)
      (conflict! {:message "Gene must be live."
                  :gene/status from-gene-status}))
    (let [cdata (stc/conform spec data)
          {biotype :gene/biotype product :product} cdata
          {p-seq-name :gene/sequence-name
           p-biotype :gene/biotype} product
          p-seq-name (get-in cdata [:product :gene/sequence-name])
          prov-from (-> request
                        (wnp/assoc-provenance cdata :event/split-gene)
                        (assoc :provenance/split-from lur))
          species (-> from-gene :gene/species :species/id)
          new-gene-data (merge {:gene/species {:species/id species}} product)
          mint-new-id? true
          new-result @(d/transact-async
                       conn
                       [[:wormbase.tx-fns/new-gene new-gene-data mint-new-id?]
                        prov-from])
          db (:db-after new-result)
          p-gene (d/entity db [:gene/sequence-name p-seq-name])
          p-gene-id (:gene/id p-gene)
          p-gene-lur [:gene/id p-gene-id]
          prov-into (-> request
                        (wnp/assoc-provenance cdata :event/split-gene)
                        (assoc :provenance/split-into p-gene-lur))
          curr-splits (map :db/id (:gene/splits (d/entity db lur)))
          new-splits (conj curr-splits (:db/id p-gene))
          update-result @(d/transact-async
                          conn
                          [[:db.fn/cas
                            lur
                            :gene/biotype
                            (d/entid db (:gene/biotype from-gene))
                            (d/entid db biotype)]
                           [:wormbase.tx-fns/set-many-ref lur :gene/splits new-splits]
                           prov-into])]
      (->> [p-gene-lur lur]
           (map (partial apply array-map))
           (zipmap [:created :updated])
           (created (str "/api/gene/" p-gene-id))))))

(defn- invert-split-tx [db e a v tx added?]
  (cond
    (= (d/ident db a) :gene/status)
    [:db.fn/cas e a v (d/entid db :gene.status/dead)]

    (= (d/ident db a) :gene/id)
    [:db/add e a v]

    :default
    [(if added? :db/retract :db/add) e a v]))

(defn undo-split-gene [request from-id into-id]
  (entity-must-exist! request from-id into-id)
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
          payload (get request :body-params {})
          prov (-> request
                   (wnp/assoc-provenance payload :event/undo-split-gene)
                   (merge {:db/id "datomic.tx"
                           :provenance/compensates tx
                           :provenance/why "Undoing split"}))
          fact-mapper (partial invert-split-tx (d/db conn))
          compensating-txes (wdb/invert-tx (d/log conn)
                                            tx
                                            prov
                                            fact-mapper)
          tx-result @(d/transact-async conn compensating-txes)]
      (ok {:live from-id :dead into-id}))
    (not-found {:message "No transaction to undo"})))


(defn change-status
  "Change the status of gene to `status`.

  `request` - the ring request.
  `identifier` must uniquely identify a
  gene.
  `to-status` - a keyword/ident identifiying a gene
  status. (e.g: :gene.status/live)
  `event-type` - keyword/ident
  `fail-precondition?` - A function that takes a single argument of
  the current gene status *entity*, and should return a truth-y value
  to indicate if a precondition-failed (HTTP 412) should be returned.
  `predcondition-failure-msg` - An optional message to send back in the
  case where fail-precondition? returns true."
  [request identifier to-status event-type
   & {:keys [fail-precondition? precondition-failure-msg]
      :or {precondition-failure-msg "gene status cannot be updated."}}]
  (entity-must-exist! request identifier)
  (let [{db :db payload :body-params} request
        lur (s/conform ::wsg/identifier identifier)
        pull-status #(d/pull % '[{:gene/status [:db/ident]}] lur)
        {gene-status :gene/status} (pull-status db)]
    (when (and gene-status
               fail-precondition?
               (fail-precondition? gene-status))
      (precondition-failed! {:message precondition-failure-msg
                             :info (wu/undatomicize gene-status)}))
    (let [prov (wnp/assoc-provenance request payload event-type)
          conn (:conn request)
          tx-res @(d/transact-async
                   conn [[:db.fn/cas
                          lur
                          :gene/status
                          (d/entid db (:db/ident gene-status))
                          (d/entid db to-status)]
                         prov])]
      (-> tx-res
          :db-after
          pull-status
          wu/undatomicize
          ok))))

(defn resurrect-gene [request id]
  (change-status request id :gene.status/live :event/resurrect-gene
                 :fail-precondition? wnu/live?
                 :precondition-failure-msg "Gene is already live."))

(defn suppress-gene [request id]
  (change-status request id :gene.status/suppressed :event/suppress-gene
                 :fail-precondition? wnu/not-live?
                 :precondition-failure-msg "Gene must have a live status."))

(defn kill-gene [request id]
  (change-status request id
                 :gene.status/dead :event/kill-gene
                 :fail-precondition? wnu/dead?
                 :precondition-failure-msg "Gene to be killed cannot be dead."))

(def default-responses
  {ok {:schema {:updated ::wsg/updated}}
   bad-request {:schema {:errors ::wsc/error-response}}
   conflict {:schema {:conflict ::wsc/error-response}}
   precondition-failed {:schema ::wsc/error-response}})

(defn response-map [m]
  (into {} (map (fn [[rf sm]] [(:status (rf)) sm]) m)))

(def status-changed-responses
  (-> default-responses
      (assoc ok {:schema ::wsg/status-changed})
      (response-map)))

(def coll-resources
  (sweet/context "/gene/" []
     :tags ["gene"]
     (sweet/resource
      {:get
       {:summary "Find genes by any name."
        :responses (-> default-responses
                       (assoc ok
                              {:schema ::wsg/find-result})
                       (response-map))
        :parameters {:query-params ::wsg/find-request}
        :x-name ::find-gene
        :handler (fn find-by-any-name [request]
                   (find-gene request))}
       :post
       {:summary "Create new names for a gene (cloned or un-cloned)"
        :middleware [wna/restrict-to-authenticated]
        :x-name ::new-gene
        :parameters {:body-params ::wsg/new}
        :responses (-> default-responses
                       (assoc created
                              {:schema {:created ::wsg/created}})
                       (assoc bad-request
                              {:schema ::wsc/error-response})
                       (response-map))
        :handler new-gene}})))

(def item-resources
  (sweet/context "/gene/:identifier" []
     :tags ["gene"]
     :path-params [identifier :- ::wsg/identifier]
     (sweet/context "/resurrect" []
       (sweet/resource
        {:post
         {:summary "Resurrect a gene."
          :x-name ::resurrect-gene
          :middleware [wna/restrict-to-authenticated]
          :responses status-changed-responses
          :handler (fn [request]
                     (resurrect-gene request identifier))}}))
     (sweet/context "/suppress" []
       (sweet/resource
        {:post
         {:summary "Suppress a gene."
          :x-name ::suppress-gene
          :middleware [wna/restrict-to-authenticated]
          :responses status-changed-responses
          :handler (fn [request]
                     (suppress-gene request identifier))}}))
     (sweet/resource
      {:delete
       {:summary "Kill a gene"
        :middleware [wna/restrict-to-authenticated]
        :x-name ::kill-gene
        :parameters {:body-params ::wsg/kill}
        :responses status-changed-responses
        :handler (fn [request]
                   (kill-gene request identifier))}})
     (sweet/resource
      {:get
       {:summary "Information about a given gene."
        :x-name ::about-gene
        :responses (-> default-responses
                       (assoc ok {:schema ::wsg/info})
                       (response-map))
        :handler (fn [request]
                   (about-gene request identifier))}
       :put
       {:summary "Add new names to an existing gene"
        :x-name ::update-gene
        :parameters {:body-params ::wsg/update}
        :middleware [wna/restrict-to-authenticated]
        :responses (-> default-responses
                       (dissoc conflict)
                       (response-map))
        :handler (fn [request]
                   (update-gene request identifier))}})
     (sweet/context "/merge/:from-identifier" [from-identifier]
       (sweet/resource
        {:post
         {:summary "Merge one gene with another."
          :middleware [wna/restrict-to-authenticated]
          :x-name ::merge-gene
          :path-params [from-identifier ::wsg/identifier]
          :parameters {:body-params {:gene/biotype :gene/biotype}}
          :responses (response-map default-responses)
          :handler
          (fn [request]
            (merge-genes request identifier from-identifier))}
         :delete
         {:summary "Undo a merge operation."
          :middleware [wna/restrict-to-authenticated]
          :x-name ::undo-merge-gene
          :path-params [from-identifier ::wsg/identifier]
          :responses (-> default-responses
                         (assoc ok {:schema ::wsg/undone})
                         (response-map))
          :handler (fn [request]
                     (undo-merge-gene request
                                      from-identifier
                                      identifier))}}))
     (sweet/context "/split" []
       (sweet/resource
        {:post
         {:summary "Split a gene."
          :middleware [wna/restrict-to-authenticated]
          :x-name ::split-genes
          :parameters {:body-params ::wsg/split}
          :responses (-> (dissoc default-responses ok)
                         (assoc created
                                {:schema ::wsg/split-response})
                         (response-map))
          :handler
          (fn [request]
            (split-gene request identifier))}}))
     (sweet/context "/split/:into-identifier" [into-identifier]
       (sweet/resource
        {:delete
         {:summary "Undo a split gene operation."
          :middleware [wna/restrict-to-authenticated]
          :x-name ::undo-split-gene
          :path-params [into-identifier :- ::wsg/identifier]
          :responses (-> default-responses
                         (assoc ok {:schema ::wsg/undone})
                         (response-map))
          :handler (fn [request]
                     (undo-split-gene request
                                      identifier
                                      into-identifier))}}))))

(def routes
  (sweet/routes
   coll-resources
   item-resources))

