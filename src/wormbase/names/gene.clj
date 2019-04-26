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
                                    internal-server-error
                                    not-found not-found!]]
   [spec-tools.core :as stc]
   [wormbase.db :as wdb]
   [wormbase.util :as wu]
   [wormbase.names.auth :as wna]
   [wormbase.names.entity :as wne]
   [wormbase.names.matching :as wnm]
   [wormbase.names.provenance :as wnp]
   [wormbase.names.util :as wnu]
   [wormbase.names.validation :refer [validate-names]]
   [wormbase.specs.common :as wsc]
   [wormbase.specs.gene :as wsg]
   [wormbase.specs.provenance :as wsp]
   [wormbase.ids.core :as wbids]))

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
                            (if-let [match (some #(re-matches % identifier) regexp-patterns)]
                              (into m [ident identifier])
                              m))
                          []
                          mm)]
    (when (seq result)
      [result (d/entity db result)])))

(def name-matching-rules
  '[[(gene-name ?pattern ?name ?eid ?attr)
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
          matching-rules (concat wnm/name-matching-rules name-matching-rules)
          term (stc/conform ::wsc/find-term pattern)
          q-result (d/q '[:find ?gid ?cgc-name ?sequence-name
                          :in $ % ?term
                          :where
                          (gene-name ?term ?name ?eid ?attr)
                          [?eid :gene/id ?gid]
                          [(get-else $ ?eid :gene/cgc-name "") ?cgc-name]
                          [(get-else $ ?eid :gene/sequence-name "") ?sequence-name]]
                        db
                        matching-rules
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

(def summary (wne/summarizer identify summary-pull-expr #{:gene/splits :gene/merges}))

(defn new-unnamed-gene [request]
  (let [{payload :body-params conn :conn} request
        prov (wnp/assoc-provenance request payload :event/new-gene)
        data (:data payload)
        spec ::wsg/new-unnamed
        names-validator (partial validate-names request)
        cdata (wnu/conform-data spec data names-validator)
        tx-data [cdata prov]]
    @(d/transact-async conn tx-data)))

(defn resolve-refs-to-dbids
  "Resolve references in a data payload to database ids for compare-and-swap operations."
  [db data]
  (let [species-lur (-> data :gene/species vec)
        species-entid (d/entid db species-lur)
        biotype-ident (get data :gene/biotype)
        biotype-entid (d/entid db biotype-ident)
        res (vec (merge data
                       (when biotype-entid
                         {:gene/biotype biotype-entid})
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

      (not (wdb/ident-exists? db into-biotype))
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
  (let [{conn :conn db :db payload :body-params} request
        data (:data payload)
        prov (wnp/assoc-provenance request data :event/merge-genes)
        into-biotype (:gene/biotype data)
        [[into-lur into-g] [from-lur from-g]] (validate-merge-request
                                               request
                                               into-id
                                               from-id
                                               into-biotype)
        txes [['wormbase.ids.core/merge-genes from-lur into-lur into-biotype] prov]
        tx-result @(d/transact-async conn txes)]
    (if-let [dba (:db-after tx-result)]
      (let [[into-gid from-gid] (map :gene/id [into-g from-g])
            [from-gene into-gene] (merged-info dba from-gid into-gid)]
        (ok {:updated into-gene
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
          compensating-txes (wdb/invert-tx (d/log conn) tx prov)
          tx-result @(d/transact-async conn compensating-txes)]
      (ok {:live into-id :dead from-id}))
    (not-found {:message "No transaction to undo"})))

(defn split-gene [request identifier]
  (let [{conn :conn db :db} request
        data (get-in request [:body-params :data] {})
        template (wbids/identifier-format db :gene/id)
        spec ::wsg/split
        [lur from-gene] (identify request identifier)
        from-gene-status (:gene/status from-gene)]
    (when (nil? from-gene)
      (not-found! {:message "Gene missing"}))
    (when (wnu/not-live? from-gene-status)
      (conflict! {:message "Gene must be live."
                  :gene/status from-gene-status}))
    (let [cdata (stc/conform spec data)
          {biotype :gene/biotype product :product} cdata
          {p-seq-name :gene/sequence-name
           p-biotype :gene/biotype} product
          p-seq-name (get-in cdata [:product :gene/sequence-name])
          prov (wnp/assoc-provenance request cdata :event/split-gene)
          species (get-in from-gene [:gene/species :species/latin-name])
          new-data (merge {:gene/species (s/conform :gene/species species)}
                          (assoc product
                                 :db/id p-seq-name
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
           (map (partial apply array-map))
           (zipmap [:created :updated])
           (created (str "/api/gene/" p-gene-id))))))

(defn- invert-split-tx [db e a v tx added?]
  (cond
    (= (d/ident db a) :gene/status)
    [:db/cas e a v (d/entid db :gene.status/dead)]

    (= (d/ident db a) :gene/id)
    [:db/add e a v]

    :default
    [(if added? :db/retract :db/add) e a v]))

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
                                           fact-mapper)
          tx-result @(d/transact-async conn compensating-txes)]
      (ok {:live from-id :dead into-id}))
    (not-found {:message "No transaction to undo"})))

(def status-changer (partial wne/status-changer ::wsg/identifier :gene/status))


(def resurrect-gene (status-changer :gene.status/live :event/resurrect-gene
                                    :fail-precondition? wnu/live?
                                    :precondition-failure-msg "Gene is already live."))

(def suppress-gene (status-changer :gene.status/suppressed :event/suppress-gene
                                   :fail-precondition? wnu/not-live?
                                   :precondition-failure-msg "Gene must be live."))

(def kill-gene (status-changer :gene.status/dead :event/kill-gene
                               :fail-precondition? wnu/dead?
                               :precondition-failure-msg "Gene to be killed is already dead."))

(def status-changed-responses (wnu/response-map ok {:schema ::wsg/status-changed}))

(def coll-resources
  (sweet/context "/gene/" []
    :tags ["gene"]
    (sweet/resource
     {:get
      {:summary "Find genes by any name."
       :responses (wnu/response-map ok {:schema ::wsg/find-result})
       :parameters {:query-params ::wsc/find-request}
       :x-name ::find-gene
       :handler (fn find-by-any-name [request]
                  (find-gene request))}
      :post
      {:summary "Create new names for a gene (cloned or un-cloned)"
       :middleware [wna/restrict-to-authenticated]
       :x-name ::new-gene
       :parameters {:body-params {:data ::wsg/new :prov ::wsp/provenance}}
       :responses (wnu/response-map created {:schema {:created ::wsg/created}}
                                    bad-request {:schema ::wsc/error-response})
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
          :middleware [wna/restrict-to-authenticated]
          :parameters {:body-params {:prov ::wsp/provenance}}
          :responses status-changed-responses
          :handler (fn [request]
                     (resurrect-gene request identifier))}}))
     (sweet/context "/suppress" []
       (sweet/resource
        {:post
         {:summary "Suppress a gene."
          :x-name ::suppress-gene
          :middleware [wna/restrict-to-authenticated]
          :parameters {:body-params {:prov ::wsp/provenance}}
          :responses status-changed-responses
          :handler (fn [request]
                     (suppress-gene request identifier))}}))
     (sweet/resource
      {:delete
       {:summary "Kill a gene"
        :middleware [wna/restrict-to-authenticated]
        :x-name ::kill-gene
        :parameters {:body-params {:prov ::wsp/provenance}}
        :responses status-changed-responses
        :handler (fn [request]
                   (kill-gene request identifier))}})
     (sweet/resource
      {:get
       {:summary "Information about a given gene."
        :x-name ::summary
        :responses (wnu/response-map ok {:schema ::wsg/summary})
        :handler (fn [request]
                   (try
                     (summary request identifier)
                     (catch Exception e
                       (prn e)
                       (throw e))))}
       :put
       {:summary "Update an existing gene."
        :x-name ::update-gene
        :parameters {:body-params {:data ::wsg/update
                                   :prov ::wsp/provenance}}
        :middleware [wna/restrict-to-authenticated]
        :responses (-> wnu/default-responses
                       (dissoc conflict)
                       (wnu/response-map))
        :handler (fn [request]
                   (let [update-gene (wne/updater identify
                                                  :gene/id
                                                  (partial wnu/conform-data ::wsg/update)
                                                  :event/update-gene
                                                  summary-pull-expr
                                                  validate-names
                                                  resolve-refs-to-dbids)]
                     (update-gene request identifier)))}})
     (sweet/context "/merge/:from-identifier" [from-identifier]
       (sweet/resource
        {:post
         {:summary "Merge one gene with another."
          :middleware [wna/restrict-to-authenticated]
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
          :middleware [wna/restrict-to-authenticated]
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
          :middleware [wna/restrict-to-authenticated]
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
          :middleware [wna/restrict-to-authenticated]
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
