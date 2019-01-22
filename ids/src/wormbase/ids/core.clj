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
  "Get the latest identifier for a given `ident`."
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
  "Get the numeric suffix of the latest identifier for a given `ident`."
  [db ident]
  (if-not (d/entid db ident)
    (throw (ex-info "Invalid ident" {:ident ident}))
    (or (parse-digits (latest-id db ident)) 0)))

(defn next-identifier
  "Get the next identifier for a given `ident`."
  [db ident template]
  (->> (latest-id-number db ident) inc (format template)))

(defn identifier-format [db uiident]
  (let [{template :template/format} (d/pull db
                                            '[:template/format]
                                            [:template/describes uiident])]
    template))

(defn new
  "Asssign new monitonically increasing identifiers in collections of mappings.

  `template` - A c-style template string that can be used to format identifiers for a `uiident`.
  `uiident` - A datomic ident that uniquely identifies an entity.
  `coll` - A collection of mappings that contain data to be transacted.
           Maps in this collection will be augmented with identifiers associated with `uiident`.

  Returns data suitable for passing to `datomic.api/transact`."
  [db template uiident coll]
  (let [start-n (latest-id-number db uiident)
        stop-n (+ (count coll) start-n)]
    (some->> (range start-n stop-n)
             (map inc)
             (map (partial format template))
             (map (partial array-map uiident))
             (interleave coll)
             (partition 2)
             (map (partial apply merge)))))

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
                         (let [cloned-values ((apply juxt attrs-signifying-cloned) into-gene)]
                           (every? nil? cloned-values)))]
    (concat
     [['wormbase.ids.core/cas-batch from-id {:gene/status :gene.status/dead}]
      ['wormbase.ids.core/cas-batch into-id {:gene/biotype into-biotype}]
      [:db/add from-id m-attr into-id]]
     (when (uncloned-gene? into-gene)
       [[:db/retract from-id :gene/sequence-name from-seq-name]
        [:db/cas into-id :gene/sequence-name nil from-seq-name]]))))

(defn split-gene
  "Split a gene into a new gene.

  Ensures the new gene product has a biotype and sequence name.

  Return transaction data."
  [db from-id new-biotype product-biotype product-sequence-name]
  (let [m-attr :gene/splits
        new-id-template (identifier-format db :gene/id)
        pull-attr-specs {:gene/biotype [:db/ident]
                         :gene/species [:species/latin-name]}
        from-gene (d/pull db (conj '[*] pull-attr-specs) from-id)
        curr-bt (d/entid db (->> (find pull-attr-specs :gene/biotype)
                                 (flatten)
                                 (get-in from-gene)))
        new-bt (d/entid db new-biotype)
        new-data {:gene/sequence-name product-sequence-name
                  :gene/biotype product-biotype
                  :gene/species (get-in from-gene [:gene/species :species/latin-name])
                  :gene/status :gene.status/live}
        p-gene-lur (find new-data :gene/sequence-name)]
  [['wormbase.ids.core/new new-id-template :gene/id [new-data]]
   [:db/add from-id :gene/splits product-sequence-name]
   [:db/cas from-id :gene/biotype curr-bt new-bt]]))

  
  
