(ns wormbase.ids.core
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.walk :as w]
   [datomic.api :as d]))

(defn attr-schema-unique? [db attr]
  (try
    (let [s-attr (:db/unique (d/entity db attr))]
      (#{:db.unique/identity :db.unique/value} s-attr))
    (catch IllegalArgumentException iae)))

(defn entids->data [db m k v]
    (assoc m
           k
           (cond
             (or (pos-int? v) (vector? v))
             (let [prs (d/pull db '[*] v)
                   uniq-keys (filter (fn [[a v]]
                                          (attr-schema-unique? db a)) prs)]
               (select-keys prs (first uniq-keys)))
             :else v)))

(defn data->entid [db m k v]
  (assoc m
         k
         (cond
           (and (map? v) (:db/id v)) (:db/id v)
           (keyword? v) [:db/ident v]
           (pos-int? v) (:db/id (d/pull db '[*] v))
           (vector? v) v
           :else v)))

(defn data->entids
  "Resolve datomic db entids to data in entity-map `em`."
  [db em]
  (w/postwalk
      (fn id-resolve [xs]
        (if (map? xs)
          (reduce-kv (partial data->entid db) {} xs)
          xs))
      em))

(defn cas-batch
  "Collate a collection of compare-and-swap operations."
  [db eid data]
  (let [entity-map (d/pull db '[*] eid)]
    (some->> data
             (data->entids db)
             (map (fn gen-cas [[k v]]
                    (let [e-data (data->entids db entity-map)
                          old-v (k e-data)]
                      (when-not (or (nil? v)
                                    (= old-v v))
                        [:db/cas eid k old-v v]))))
             (remove nil?))))

(defn latest-id
  "Returns the latest WB identifier for a given `ident` as a string."
  [db ident]
  (d/q '[:find (max ?gid) .
         :in $ ?ident
         :where
         [?e ?ident ?gid]]
       (d/history db)
       ident))

(s/fdef parse-digits
  :args (s/cat :identifier string?)
  :ret (s/nilable int?)
  :fn #(str/includes? (:args %) (-> % :ret str)))
(defn parse-digits
  "Parse digits from an identifier."
  [identifier]
  (some->> identifier
           (re-seq #"0*(\d+)")
           (flatten)
           (last)
           (read-string)))

(defn latest-id-number
  "Returns the numeric value for the latest WB idenifier given an entity `ident`."
  [db ident]
  (when-not (d/entid db ident)
    (throw (ex-info "Invalid ident" {:ident ident})))
  (let [counter-ident (keyword "counter" (namespace ident))]
    (when-not counter-ident
      (throw (ex-info "Invalid ident"
                      {:ident counter-ident})))
    (get (d/pull db [counter-ident] counter-ident) counter-ident 0)))

(defn next-identifier
  "Return the next identifier for a given `ident`."
  [db ident template]
  (->> (latest-id-number db ident)
       (inc)
       (biginteger)
       (format template)))

(defn identifier-format [db uiident]
  (let [{template :format} (d/pull db
                                   '[[:wormbase.names/id-template-format :as :format]]
                                   [:db/ident uiident])]
    template))

(defn new
  "Asssign new monitonically increasing identifiers in collections of mappings.

  `template` - A c-style template string that can be used to format identifiers for a `uiident`.
  `uiident` - A datomic ident that uniquely identifies an entity.
  `coll` - A collection of mappings that contain data to be transacted.
           Maps in this collection will be augmented with identifiers associated with `uiident`.

  Looks-up the highest numeric id stored against an entity.
  Increments then applies the datomic compare-and-swap function: (+ curr-max-id (count coll)
  returns sequnce of maps, suitable for `datomic.api/transact`."
  [db template uiident coll & {:keys [start-n]
                               :or {start-n (latest-id-number db uiident)}}]
  (let [stop-n (+ (count coll) start-n)
        counter-ident (keyword "counter" (namespace uiident))
        data (some->> (range start-n stop-n)
                      (map inc)
                      (map biginteger)
                      (map (partial format template))
                      (map (partial array-map uiident))
                      (interleave coll)
                      (partition 2)
                      (map (partial apply merge)))]
    (conj data [:db/cas counter-ident counter-ident start-n stop-n])))

(defn merge-genes
  "Merge two genes.

  Transfer (retract and add) sequence-name from the \"from-gene\" if the \"into-gene\" is uncloned.
  The \"from-gene\" will be marked as dead.

  Return transaction data."
  [db from-id into-id into-biotype]
  (let [m-attr :gene/merges
        attrs-signifying-cloned [:gene/biotype :gene/sequence-name]
        from-gene (d/pull db [:gene/sequence-name] from-id)
        into-gene (d/pull db attrs-signifying-cloned into-id)
        from-seq-name (:gene/sequence-name from-gene)
        uncloned-gene? (fn uncloned? [gene]
                         (let [cloned-values ((apply juxt attrs-signifying-cloned) gene)]
                           (every? nil? cloned-values)))]
    (concat
     [['wormbase.ids.core/cas-batch from-id {:gene/status :gene.status/dead}]
      ['wormbase.ids.core/cas-batch into-id {:gene/biotype into-biotype}]
      [:db/add from-id m-attr into-id]]
     (when (uncloned-gene? into-gene)
       [[:db/retract from-id :gene/sequence-name from-seq-name]
        [:db/cas into-id :gene/sequence-name nil from-seq-name]]))))

(defn split-genes
  "Split a gene into a new gene.

  Ensures the new gene product has a biotype and sequence name.

  Return transaction data."
  [db xs]
  (let [pull-attr-specs {:gene/biotype [:db/ident]
                         :gene/species [:species/latin-name]}
        pull-from-gene (partial d/pull db (conj '[*] pull-attr-specs))
        id-template (identifier-format db :gene/id)
        new-data (map (fn [{:keys [from-id product-sequence-name product-biotype]}]
                        (let [from-gene (pull-from-gene from-id)
                              from-species (:gene/species from-gene)]
                          {:db/id product-sequence-name
                           :gene/sequence-name product-sequence-name
                           :gene/biotype product-biotype
                           :gene/species (find from-species :species/latin-name)
                           :gene/status :gene.status/live}))
                      xs)]
    (some->> xs
             (mapcat (fn [{:keys [from-id new-biotype product-sequence-name]}]
                       (let [from-gene (pull-from-gene from-id)
                             curr-bt (d/entid db (->> (find pull-attr-specs :gene/biotype)
                                                      (flatten)
                                                      (get-in from-gene)))
                             new-bt (or (d/entid db new-biotype) curr-bt)]
                         [[:db/add from-id :gene/splits product-sequence-name]
                          [:db/add from-id :gene/biotype curr-bt new-bt]])))
             (cons ['wormbase.ids.core/new id-template :gene/id new-data]))))
