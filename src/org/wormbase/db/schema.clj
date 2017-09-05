(ns org.wormbase.db.schema
  (:require
   [clojure.string :as str]
   [datomic.api :as d]
   [datomic.schema :as schema :refer [defschema]]
   [io.rkn.conformity :as c]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [org.wormbase.species :as wb-species]))

(defschema Species
  (schema/partition :db.part/species)
  (schema/attrs
   ^{:added "1.0"}
   [:id :keyword {:unique :value
                  :doc "Species primary identifier."}]
   [:latin-name :string {:unique :value
                         :doc "Species full latin name."}]))

(defschema GeneStatus
  (schema/partition :db.part/gene.status)
  (schema/enums
   :status
   {:db/ident :gene.status/live}
   {:db/ident :gene.status/dead}
   {:db/ident :gene.status/suppressed}))

(defschema GeneBiotype
  (schema/partition :db.part/gene.biotype)
  (schema/enums
   :biotype
   {:db/ident :gene.biotype/cds}
   {:db/ident :gene.biotype/pseudogene}
   {:db/ident :gene.biotype/transcript}
   {:db/ident :gene.biotype/transposon}))

(defschema Gene
  (schema/partition :db.part/gene)
  (schema/attrs
   [:id :string {:unique :identity
                 :doc "Primary identifier for a Gene."}]
   [:biotype #'GeneBiotype]
   [:status #'GeneStatus]
   [:species #'Species {:doc "Reference to a Species."}]
   [:sequence-name :string {:unique :value
                            :doc "The sequence-name of the Gene."}]
   [:cgc-name :string {:unique :value
                       :doc "CGC name of Gene."}]))

(defschema UserRole
  (schema/partition :db.part/user.role)
  (schema/enums
   :role
   {:db/ident :user.role/admin}))

(defschema UserAgent
  (schema/partition :db.part/user.agent)
  (schema/enums
   :agent
   {:db/ident :user.agent/script}
   {:db/ident :user.agent/importer}
   {:db/ident :user.agent/rest-api}
   {:db/ident :user.agent/web-app}))

(defschema User
  (schema/partition :db.part/wormbase-users)
  (schema/attrs
   [:email :string {:unique :value
                    :doc "Primary identifier for a staff member."}]
   [:role #'UserRole]))

(defschema Provenance
  (schema/partition :db.part/provenance)
  (schema/attrs
   [:who #'User]
   [:when :instant {:doc "The time of the action."}]
   [:why :string {:doc "The operation (e.g splitFrom)"}]
   [:how #'UserAgent]))

(defschema Template
  (schema/partition :db.part/wormbase-name-templates)
  (schema/attrs
   [:describes
    :keyword
    {:unique :value
     :doc "The entity attribute the template format describes."}]
   [:format :string {:unique :value}]))

(defschema Functions
  (schema/partition :db.part/wb.dbfns)

  (schema/raws
   ;; templates
   ;; Define db fns in a raw data structure
   ;; since datomic.schema's function macro
   ;; does not provide a way of specifying requirements
   ;; e.g: clojure.walk, clojure.spec etc

   {:db/ident :wb.dbfns/latest-id
    :db/doc "Get the latest identifier for a given ident."
    :db/fn
    (datomic.api/function
     {:params '[db ident]
      :lang "clojure"
      :code
      '(some->> (d/datoms db :avet ident)
                (sort-by (comp d/tx->t :tx))
                (last)
                (:v))})}

   {:db/ident :wb.dbfns/latest-id-number
    :db/doc
    "Get the numeric suffix of the latest identifier for a given ident."
    :db/fn
    (datomic.api/function
     {:params '[db ident]
      :lang "clojure"
      :code
      '(let [latest-identifier (d/invoke db :wb.dbfns/latest-id db ident)
             latest-n (if latest-identifier
                        (->> (re-seq #"[0]+(\d+)" latest-identifier)
                             flatten
                             last
                             read-string)
                        -1)]
         (if latest-identifier
           latest-n
           0))})}
   
   {:db/ident :wb.dbfns/new-names
    :db/doc "Allocate a new name for entity"
    :db/fn
    (datomic.api/function
     {:lang "clojure"
      :requires '[[clojure.walk :as w]
                  [clojure.spec.alpha :as s]]
      :params '[db entity-type name-records spec]
      :code
      '(if (s/valid? spec name-records)
         (let [ident (keyword entity-type "id")
               template (-> (d/entity db [:template/describes ident])
                            :template/format)
               status-ident (keyword entity-type "status")
               live-ident (keyword (str entity-type ".status") "live")
               start-n (d/invoke db :wb.dbfns/latest-id-number db ident)
               stop-n (some-> name-records count inc)
               identify-rec (fn [idx rec]
                              (let [next-n (+ 1 idx start-n)
                                    next-id (format template next-n)
                                    temp-part (keyword "db.part"
                                                       entity-type)
                                    temp-id (d/tempid temp-part)
                                    species-id (:gene/species rec)
                                    species-lur [:species/id species-id]]
                                (merge
                                 (-> (assoc rec ident next-id)
                                     (dissoc :gene/species)
                                     (assoc :gene/species species-lur))
                                 {:db/id temp-id}
                                 {status-ident live-ident})))
               new-names (->> name-records
                              (map w/keywordize-keys)
                              (map-indexed identify-rec)
                              (vec))]
           new-names)
         (throw (ex-info "Not valid according to spec."
                         {:problems [s/explain-data spec name-records]
                          :records name-records})))})}))

(def worms
  ["Caenorhabditis elegans"
   "Caenorhabditis briggsae"
   "Caenorhabditis remanei"
   "Caenorhabditis brenneri"
   "Pristionchus pacificus"
   "Caenorhabditis japonica"
   "Brugia malayi"
   "Onchocerca volvulus"
   "Strongyloides ratti"])

(def seed-data
  {:species
   (->> (map wb-species/latin-name->ident worms)
        (interleave (repeat (count worms) :species/id))
        (partition 2)
        (map (partial apply hash-map))
        (vec))
   :templates
   [{:template/format "WBGene%08d"
     :template/describes :gene/id}
    {:template/format "WBsf%12d"
     :template/describes :feature/id}
    {:template/format "WBVar%08d"
     :template/describes :variation/id}]
   :users
   [{:user/email "matthew.russell@wormbase.org"}]})

;; TODO: conformity uses `schema-ident` to uniquely identity idempotent
;;       schema transactions.
;;       find a way to version the schema by passing this in.
;;       e.g: could be release number (WS\d+), or a timestamp-ed string
(defn install [conn run-n]
  (let [schema-ident (keyword (str "schema-" run-n))]
    (->> (schema/schemas *ns*)
         (apply schema/tx-datas)
         (assoc-in {} [schema-ident :txes])
         (c/ensure-conforms conn))
    ;; seed data
    (let [seeds (->> seed-data
                     ((apply juxt (keys seed-data)))
                     (assoc-in {} [:txes]))]
      (c/ensure-conforms conn {::seed-data seeds}))))

(defn species [data]
  (->Species data))


