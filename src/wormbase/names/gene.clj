(ns wormbase.names.gene
  (:require
   [clojure.spec.alpha :as s]
   [compojure.api.sweet :as sweet]
   [datomic.api :as d]
   [expound.alpha :refer [expound-str]]
   [ring.util.http-response :refer [ok
                                    bad-request
                                    bad-request!
                                    conflict conflict!
                                    created
                                    internal-server-error
                                    not-found not-found!]]
   [wormbase.db :as wdb]
   [wormbase.util :as wu]
   [wormbase.names.entity :as wne]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.names.validation :refer [validate-names]]
   [wormbase.specs.common :as wsc]
   [wormbase.specs.validation :as wsv]
   [wormbase.specs.gene :as wsg]
   [wormbase.specs.provenance :as wsp]
   [wormbase.ids.core :as wbids]))

(defmethod wne/transform-ident-ref-value :gene/biotype [_ m]
  (wnu/transform-ident-ref :gene/biotype m "biotype"))

(defmethod wne/transform-ident-ref-value :into-biotype [_ m]
  (wnu/transform-ident-ref :into-biotype m "biotype"))

(defmethod wne/transform-ident-ref-value :gene/status [_ m]
  (wnu/transform-ident-ref :gene/status m "gene.status"))

(defmethod wne/transform-ident-ref-value :gene/species [_ m]
  (update m :gene/species (fn [x]
                            (cond
                              (vector? x) x
                              (string? x) [:species/latin-name x]
                              :else x))))

(defmethod validate-names :gene [request data]
  (when-not (some-> request :params :force)
    (let [db (:db request)
          species-lur (:gene/species data)
          species-ent (d/pull db '[*] species-lur)]
      (when (empty? data)
        (throw (ex-info "No names to validate (empty data)"
                        {:type :user/validation-error})))
      (when-not (:db/id species-ent)
        (throw (ex-info "Invalid species specified"
                        {:type :user/validation-error})))
      (let [patterns ((juxt :species/cgc-name-pattern :species/sequence-name-pattern) species-ent)
            regexps (map re-pattern patterns)
            name-idents [:gene/cgc-name :gene/sequence-name]]
        (->> (interleave regexps name-idents)
             (partition 2)
             (map (fn collect-errors [[regexp name-ident]]
                    (when-let [gname (name-ident data)]
                      (when-not (re-matches regexp gname)
                          {:name (name name-ident)
                           :value gname
                           :reason "Did not match species regular expression pattern."
                           :regexp (str regexp)}))))
             (filter identity)
             (seq))))))

(def delete-responses (assoc wnu/default-responses ok {:schema {:updated ::wsg/updated}}))

(defn- all-regexp-patterns-for [db ident]
  (map re-pattern
       (d/q '[:find [?regex ...]
              :in $ ?ident
              :where
              [_ ?ident ?regex]]
            db
            ident)))

(defn identify
  "Identify a gene given an `identifier` string.
  Return a tuple of lookup-ref and corrsponding entity from the database,
  or nil if none found."
  [request ^String identifier]
  (let [{db :db} request
        mm {:gene/sequence-name (all-regexp-patterns-for db :species/sequence-name-pattern)
            :gene/cgc-name (all-regexp-patterns-for db :species/cgc-name-pattern)
            :gene/id #{wsg/gene-id-regexp}}
        result (reduce-kv (fn [m ident regexp-patterns]
                            (if (some #(re-matches % identifier) regexp-patterns)
                              (into m [ident identifier])
                              m))
                          []
                          mm)]
    (when (seq result)
      (when-let [ent (d/entity db result)]
        [result ent]))))

(def summary-pull-expr '[* {:gene/biotype [[:db/ident]]
                            :gene/species [[:species/latin-name]]
                            :gene/status [[:db/ident]]
                            [:gene/merges :as :merged-into] [[:gene/id]]
                            [:gene/_merges :as :merged-from] [[:gene/id]]
                            [:gene/splits :as :split-into] [[:gene/id]]
                            [:gene/_splits :as :split-from] [[:gene/id]]}])

(defmethod wnp/resolve-change :gene/id
  [db change]
  (when-let [found (wnu/resolve-refs db (find change :gene/id))]
    (assoc change :value (:gene/id found))))

(defmethod wnp/resolve-change :gene/species
  [db change]
  (when-let [found (wnu/resolve-refs db {:gene/species (:value change)})]
    (assoc change
           :value
           (get-in found [:gene/species :species/latin-name]))))

(defn- resolve-ref-to-gene-id
  [attr db change]
  (let [found (wnu/resolve-refs db {attr (:value change)})]
    (assoc change :value (get-in found [attr :gene/id]))))

(defmethod wnp/resolve-change :gene/merges
  [db change]
  (resolve-ref-to-gene-id :gene/merges db change))

(defmethod wnp/resolve-change :gene/splits
  [db change]
  (resolve-ref-to-gene-id :gene/splits db change))

(defn resolve-refs-to-dbids
  "Resolve references in a data payload to database ids for compare-and-swap operations."
  [db data]
  (let [species-lur (-> data :gene/species vec)
        species-entid (d/entid db species-lur)
        biotype-ident (when-let [bt (get data :gene/biotype)]
                        (if (string? bt)
                          (keyword "biotype" bt)
                          bt))
        biotype-entid (d/entid db biotype-ident)
        res (vec (merge data
                        (when biotype-entid
                          {:gene/biotype biotype-ident})
                        (when species-entid
                          {:gene/species species-entid})))]
    res))

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

      (not (wdb/ident-exists? db (keyword "biotype" into-biotype)))
      (throw (ex-info "Biotype does not exist"
                      {:type ::wdb/missing
                       :entity [:db/ident into-biotype]
                       :problems "Biotype entity does not exist"})))
    (when (reduce not= (map :gene/species [from-gene into-gene]))
      (throw (ex-info
              "Refusing to merge: genes have differing species"
              {:from {:gen/species (-> from-gene :gene/species)
                      :gene/id from-gene-id}
               :into {:species (-> into-gene :gene/species)
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

(defn merged-info [db id & more-ids]
  (some->> (cons id more-ids)
           (map (partial vector :gene/id))
           (map (partial d/pull db summary-pull-expr))
           (wu/elide-db-internals db)))

(defn merge-genes [request into-id from-id]
  (let [{conn :conn payload :body-params} request
        data (:data payload)
        prov (wnp/assoc-provenance request payload :event/merge-genes)
        [[into-lur into-g] [from-lur from-g]] (validate-merge-request
                                               request
                                               into-id
                                               from-id
                                               (:biotype data))
        into-biotype (keyword "biotype" (:biotype data))
        txes [['wormbase.ids.core/merge-genes from-lur into-lur into-biotype] prov]
        tx-result @(d/transact-async conn txes)]
    (if-let [dba (:db-after tx-result)]
      (let [[into-gid from-gid] (map :gene/id [into-g from-g])
            [from-gene into-gene] (merged-info dba from-gid into-gid)]
        (ok {:updated (wnu/unqualify-keys into-gene "gene")
             :merges (:gene/merges from-gene)
             :statuses {from-id (:gene/status from-gene)
                        into-id (:gene/status into-gene)}}))
      (internal-server-error
       {:message "Errant program logic."}))))

(defn undo-merge-gene [request into-id from-id]
  (if-let [tx (d/q '[:find ?tx .
                     :in $ ?into ?from
                     :where
                     [?from :gene/merges ?into ?tx]]
                   (-> request :db d/history)
                   [:gene/id into-id]
                   [:gene/id from-id])]
    (let [conn (:conn request)
          payload (get request :body-params {})
          prov (-> request
                   (wnp/assoc-provenance payload :event/undo-merge-genes)
                   (merge {:db/id "datomic.tx"
                           :provenance/compensates tx
                           :provenance/why "Undoing merge"}))
          compensating-txes (wdb/invert-tx (d/log conn) tx prov)]
      @(d/transact-async conn compensating-txes)
      (ok {:live into-id :dead from-id}))
    (not-found {:message "No transaction to undo"})))

(defn split-gene [request identifier]
  (let [{conn :conn db :db} request
        payload (:body-params request)
        data (:data payload)
        template (wbids/identifier-format db :gene/id)
        [lur from-gene] (identify request identifier)
        from-gene-status (:gene/status from-gene)]
    (when (nil? from-gene)
      (not-found! {:message "Gene missing"}))
    (when (wnu/not-live? from-gene-status)
      (conflict! {:message "Gene must be live."
                  :gene/status from-gene-status}))
    (let [product (-> data
                      :product
                      (wnu/qualify-keys "gene")
                      (wne/transform-ident-ref-values))
          cdata (-> data
                    (dissoc :product)
                    (wnu/qualify-keys "gene")
                    (assoc :product product)
                    (wne/transform-ident-ref-values))
          {biotype :gene/biotype product :product} cdata
          {p-seq-name :gene/sequence-name
           p-biotype :gene/biotype} product
          prov (wnp/assoc-provenance request payload :event/split-gene)
          species (get-in from-gene [:gene/species :species/latin-name])
          new-data (merge {:gene/species (s/conform :gene/species species)}
                          (assoc product
                                 :db/id p-seq-name
                                 :gene/biotype p-biotype
                                 :gene/status :gene.status/live))
          curr-bt (d/entid db (:gene/biotype from-gene))
          new-bt (d/entid db biotype)
          p-gene-lur [:gene/sequence-name p-seq-name]
          xs [new-data]
          txes [['wormbase.ids.core/new template :gene/id xs]
                [:db/add lur :gene/splits p-seq-name]
                [:db/cas lur :gene/biotype curr-bt new-bt]
                prov]
          tx-result @(d/transact-async conn txes)
          dba (:db-after tx-result)
          from-gene-lur (find from-gene :gene/id)
          p-gene (wdb/pull dba summary-pull-expr p-gene-lur)
          p-gene-id (:gene/id p-gene)
          p-gene-lur* [:gene/id p-gene-id]]
      (->> [p-gene-lur* from-gene-lur]
           (map (fn [x]
                  (cons (-> x first name keyword) (rest x))))
           (map (partial apply array-map))
           (zipmap [:created :updated])
           (created (str "/api/gene/" p-gene-id))))))

(defn- invert-split-tx [db e a v _ added?]
  (let [attr (d/ident db a)]
    (cond
      (= attr :gene/status) [:db/cas e a v (d/entid db :gene.status/dead)]
      (= attr :gene/id) [:db/add e a v]
      (= attr :counter/gene) nil ;; don't change anything to do with the counter value.
      :else [(if added? :db/retract :db/add) e a v])))

(defn undo-split-gene [request from-id into-id]
  (if-let [tx (d/q '[:find ?tx .
                     :in $ ?from ?into
                     :where
                     [?from :gene/splits ?into ?tx]]
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
                                           fact-mapper)]
      @(d/transact-async conn compensating-txes)
      (ok {:live from-id :dead into-id}))
    (not-found {:message "No transaction to undo"})))

(def status-changed-responses (wnu/response-map ok {:schema ::wsg/status-changed}))

(def coll-resources
  (sweet/context "/gene" []
    :tags ["gene"]
    (sweet/resource
     {:get
      {:summary "Find genes by any name."
       :responses (wnu/http-responses-for-read {:schema ::wsg/find-result})
       :parameters {:query-params ::wsc/find-request}
       :x-name ::find-gene
       :handler (wne/finder "gene" ["cgc-name" "sequence-name" "id" "other-names"])}
      :post
      {:summary "Create new names for a gene (cloned or un-cloned)"
       :x-name ::new-gene
       :parameters {:body-params {:data ::wsg/new :prov ::wsp/provenance}}
       :responses (wnu/response-map created {:schema {:created ::wsg/created}}
                                    bad-request {:schema ::wsv/error-response})
       :handler (fn handle-new [request]
                  (let [new-gene (wne/creator :gene/id
                                              (partial wnu/conform-data-drop-label ::wsg/new)
                                              :event/new-gene
                                              summary-pull-expr
                                              validate-names)]
                    (new-gene request)))}})))

(def item-resources
  (sweet/context "/gene/:identifier" []
    :tags ["gene"]
    :path-params [identifier :- ::wsg/identifier]
    (sweet/context "/resurrect" []
      (sweet/resource
       {:post
        {:summary "Resurrect a gene."
         :x-name ::resurrect-gene
         :parameters {:body-params {:prov ::wsp/provenance}}
         :responses status-changed-responses
         :handler (fn [request]
                    (let [resurrect (wne/status-changer
                                     :gene/id
                                     :gene/status
                                     :gene.status/live
                                     :event/resurrect-gene
                                     :fail-precondition? wnu/live?
                                     :precondition-failure-msg "Gene is already live.")]
                      (resurrect request identifier)))}}))
    (sweet/context "/suppress" []
      (sweet/resource
       {:post
        {:summary "Suppress a gene."
         :x-name ::suppress-gene
         :parameters {:body-params {:prov ::wsp/provenance}}
         :responses status-changed-responses
         :handler (fn [request]
                    (let [suppress (wne/status-changer
                                    :gene/id
                                    :gene/status
                                    :gene.status/suppressed
                                    :event/suppress-gene
                                    :fail-precondition? wnu/not-live?
                                    :precondition-failure-msg "Gene must be live.")]
                      (suppress request identifier)))}}))
    (sweet/resource
     {:delete
      {:summary "Kill a gene"
       :x-name ::kill-gene
       :parameters {:body-params {:prov ::wsp/provenance}}
       :responses status-changed-responses
       :handler
       (fn [request]
         (let [kill (wne/status-changer
                     :gene/id
                     :gene/status
                     :gene.status/dead
                     :event/kill-gene
                     :fail-precondition? wnu/dead?
                     :precondition-failure-msg "Gene to be killed is already dead.")]
           (kill request identifier)))}})
    (sweet/resource
     {:get
      {:summary "Information about a given gene."
       :x-name ::summary
       :responses (wnu/http-responses-for-read {:schema ::wsg/summary})
       :handler (fn [request]
                  ((wne/summarizer identify
                                   summary-pull-expr
                                   #{:gene/splits :gene/merges})
                   request
                   identifier))}
      :put
      {:summary "Update an existing gene."
       :x-name ::update-gene
       :parameters {:body-params {:data ::wsg/update
                                  :prov ::wsp/provenance}}
       :responses (-> wnu/default-responses
                      (dissoc conflict)
                      (assoc bad-request {:schema ::wsv/error-response})
                      (wnu/response-map))
       :handler (fn [request]
                  (let [update-gene (wne/updater identify
                                                 :gene/id
                                                 (partial wnu/conform-data ::wsg/update)
                                                 :event/update-gene
                                                 summary-pull-expr
                                                 validate-names
                                                 resolve-refs-to-dbids)
                        data (some-> request :body-params :data)]
                    (when (and
                           (not (s/valid? ::wsg/cloned data))
                           (s/valid? ::wsg/uncloned data)
                           (nil? (:cgc-name data)))
                      (bad-request! {:message "CGC name cannot be removed from an uncloned gene."}))
                    (update-gene request identifier)))}})
    (sweet/context "/update-other-names" []
      (sweet/resource
       {:put
        {:summary "Add a set of other-names to a gene."
         :x-name ::add-other-names-gene
         :parameters {:body-params {:data ::wsg/update-other-names
                                    :prov ::wsp/provenance}}
         :responses (-> wnu/default-responses
                        (dissoc conflict)
                        (assoc bad-request {:schema ::wsv/error-response})
                        (wnu/response-map))
         :handler (fn [request]
                    (let [add-other-names (wne/update-multi-card identify :gene/id :event/add-gene-other-names :db/add ':gene/other-names)]
                      (add-other-names request identifier)))}
        :delete
        {:summary "Delete a set of other-names from a gene."
         :x-name ::delete-other-names-gene
         :parameters {:body-params {:data ::wsg/update-other-names
                                    :prov ::wsp/provenance}}
         :responses (-> wnu/default-responses
                        (dissoc conflict)
                        (assoc bad-request {:schema ::wsv/error-response})
                        (wnu/response-map))
         :handler (fn [request]
                    (let [delete-other-names (wne/update-multi-card identify :gene/id :event/retract-gene-other-names :db/retract ':gene/other-names)]
                      (delete-other-names request identifier)))}}))
    (sweet/context "/merge/:from-identifier" [from-identifier]
      (sweet/resource
       {:post
        {:summary "Merge one gene with another."
         :x-name ::merge-gene
         :path-params [from-identifier ::wsg/identifier]
         :parameters {:body-params {:data ::wsg/merge
                                    :prov ::wsp/provenance}}
         :responses (wnu/response-map delete-responses)
         :handler
         (fn [request]
           (merge-genes request identifier from-identifier))}
        :delete
        {:summary "Undo a merge operation."
         :x-name ::undo-merge-gene
         :path-params [from-identifier ::wsg/identifier]
         :parameters {:body-params {:prov ::wsp/provenance}}
         :responses (-> delete-responses
                        (assoc ok {:schema ::wsg/undone})
                        (wnu/response-map))
         :handler (fn [request]
                    (undo-merge-gene request
                                     identifier
                                     from-identifier))}}))
    (sweet/context "/split" []
      (sweet/resource
       {:post
        {:summary "Split a gene."
         :x-name ::split-gene
         :parameters {:body-params {:data ::wsg/split :prov ::wsp/provenance}}
         :responses (-> (dissoc wnu/default-responses ok)
                        (assoc created
                               {:schema ::wsg/split-response})
                        (wnu/response-map))
         :handler
         (fn [request]
           (split-gene request identifier))}}))
    (sweet/context "/split/:into-identifier" [into-identifier]
      (sweet/resource
       {:delete
        {:summary "Undo a split gene operation."
         :x-name ::undo-split-gene
         :path-params [into-identifier :- ::wsg/identifier]
         :responses (-> delete-responses
                        (assoc ok {:schema ::wsg/undone})
                        (wnu/response-map))
         :handler (fn [request]
                    (undo-split-gene request
                                     identifier
                                     into-identifier))}}))))

(def routes (sweet/routes coll-resources
                          item-resources))
