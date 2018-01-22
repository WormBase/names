(ns org.wormbase.db.schema
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.spec.alpha :as s]
   [datomic.api :as d]
   [io.rkn.conformity :as c]
   [org.wormbase.species :as ows]
   [org.wormbase.specs.biotype :as owsb]
   [org.wormbase.specs.gene :as owsg]
   [org.wormbase.specs.species :as owss]
   [org.wormbase.specs.provenance :as owsp]
   [org.wormbase.specs.template :as owst]
   [org.wormbase.specs.user :as owsu]
   [provisdom.spectomic.core :as spectomic]))

;; datomic.schema doesn't register specs for these...
;; (s/def :db.type/instant inst?)
;; (s/def :db.type/uuid uuid?)

;; (defschema Species
;;   (ds/partition :db.part/wormbase.species)
;;   (ds/attrs
;;    ^{:added "1.0"}
;;    [:id :keyword {:unique :value
;;                   :doc "Species primary identifier."}]
;;    [:latin-name :string {:unique :value
;;                          :doc "Species full latin name."}]))

;; (defschema GeneStatus
;;   (ds/partition :db.part/gene.status)
;;   (ds/enums
;;    :status
;;    {:db/ident :gene.status/live}
;;    {:db/ident :gene.status/dead}
;;    {:db/ident :gene.status/suppressed}))

;; (defschema GeneBiotype
;;   (ds/partition :db.part/gene.biotype)
;;   (ds/enums
;;    :biotype
;;    {:db/ident :gene.biotype/cds}
;;    {:db/ident :gene.biotype/pseudogene}
;;    {:db/ident :gene.biotype/transcript}
;;    {:db/ident :gene.biotype/transposon}))

;; (defschema Gene
;;   (ds/partition :db.part/gene)
;;   (ds/attrs
;;    [:id :string {:unique :identity
;;                  :doc "Primary identifier for a Gene."}]
;;    [:biotype #'GeneBiotype]
;;    ;; [:species #'Species {:doc "Reference to a Species."}]
;;    [:sequence-name :string {:unique :value
;;                             :doc "The sequence-name of the Gene."}]
;;    [:cgc-name :string {:unique :value
;;                        :doc "CGC name of Gene."}]))

;; (defschema UserRole
;;   (ds/partition :db.part/wormbase.user)
;;   ;; TODO: do roles need to be "domain" specific?
;;   (ds/enums
;;    :role
;;    {:db/ident :user.role/admin}
;;    {:db/ident :user.role/name}
;;    {:db/ident :user.role/kill}
;;    {:db/ident :user.role/merge}
;;    {:db/ident :user.role/split}
;;    {:db/ident :user.role/view}))

;; ;; Not all attibutes of Operation may apply to every entity (Feature,
;; ;; Gene, Variation)     
;; (defschema NamesOperation
;;   (ds/partition :db.part/wormbase.names-operation)
;;   (ds/enums
;;    :ops
;;    ;; A new name is asserted for an entity
;;    {:db/ident :wormbase.names-operation/add}

;;    ;; The biotype of gene is retracted
;;    ;; the new biotype is asserted
;;    {:db/ident :wormbase.names-operation/change-biotype}

;;    ;; entity gets retracted
;;    {:db/ident :wormbase.names-operation/kill}

;;    ;; One entity gets retracted, attributes are copied to target.
;;    {:db/ident :wormbase.names-operation/merge}

;;    ;; a new entity is asserted
;;    {:db/ident :wormbase.names-operation/new}

;;    ;; a name is retracted from an existing entity
;;    {:db/ident :wormbase.names-operation/remove}

;;    ;; A new entity is created
;;    ;;  - attributes are retracted from an existing entity
;;    ;;  - these attributes are copied to the newly created entity
;;    {:db/ident :wormbase.names-operation/split}))

;; (defschema UserAgent
;;   (ds/partition :db.part/wormbase.user)
;;   (ds/enums
;;    :agent
;;    {:db/ident :user.agent/script}
;;    {:db/ident :user.agent/importer}
;;    {:db/ident :user.agent/rest-api}
;;    {:db/ident :user.agent/web-app}))

;; (defschema User
;;   (ds/partition :db.part/wormbase.user)
;;   (ds/attrs
;;    [:email :string {:unique :value
;;                     :doc "Primary identifier for a staff member."}]
;;    [:roles #'UserRole {:cardinality :many
;;                        :doc "Roles a user may have."}]))

;; (defschema Provenance
;;   (ds/partition :db.part/wormbase.provenance)
;;   (ds/attrs
;;    [:batch :uuid {:doc (str "Logical identifier for ease of reverting work."
;;                             "Work could potentially span multiple transactions"
;;                             "should be assigned the same batch identifier.")}]
;;    [:how #'UserAgent {:doc "How the operation was done to the system."}]
;;    [:when :instant {:doc "The time of the action stored in UTC."}]      
;;    [:who #'User {:doc "The user who perfomed the operation."}]
;;    [:why #'NamesOperation {:doc "The operation performed."}]))

;; (defschema Template
;;   (ds/partition :db.part/wormbase.name-templates)
;;   (ds/attrs
;;    [:describes
;;     :keyword
;;     {:unique :value
;;      :doc "The entity attribute the template format describes."}]
;;    [:format :string {:unique :value}]))

;; (defschema Functions
;;   (ds/partition :db.part/wormbase.dbfns)

;;   ;; Here we define datomic tx fns in a raw data structure
;;   ;; since datomic.schema's function macro
;;   ;; does not provide a way of specifying requirements
;;   ;; e.g: clojure.walk, clojure.spec etc
  
;;   (ds/raws
;;    {:db/ident :wormbase.dbfns/new-users
;;     :db/doc "Create new users defined in `user-records`."
;;     :db/fn
;;     (d/function
;;      '{:lang "clojure"
;;        :requires [[clojure.spec.alpha :as s]]
;;        :params [db user-records spec]
;;        :code
;;        (if (s/valid? spec user-records)
;;          (d/transact conn user-records)
;;          (throw (ex-info
;;                  "Invalid user records"
;;                  {:problems (s/explain-data spec user-records)})))})}

;;    {:db/ident :wormbase.dbfns/latest-id
;;     :db/doc "Get the latest identifier for a given `ident`."
;;     :db/fn
;;     (d/function
;;      '{:params [db ident]
;;        :lang "clojure"
;;        :code
;;        (some->> (d/datoms db :avet ident)
;;                 (sort-by (comp d/tx->t :tx))
;;                 (last)
;;                 (:v))})}

;;    {:db/ident :wormbase.dbfns/latest-id-number
;;     :db/doc
;;     "Get the numeric suffix of the latest identifier for a given `ident`."
;;     :db/fn
;;     (d/function
;;      '{:params [db ident]
;;        :lang "clojure"
;;        :code
;;        (if-let [latest-identifier (d/invoke db
;;                                             :wormbase.dbfns/latest-id
;;                                             db
;;                                             ident)]
;;          (->> (re-seq #"[0]+(\d+)" latest-identifier)
;;               (flatten)
;;               (last)
;;               (read-string))
;;          0)})}
   
;;    {:db/ident :wormbase.dbfns/new-name
;;     :db/doc "Allocate a new name for entity"
;;     :db/fn
;;     (d/function
;;      '{:lang "clojure"
;;        :requires [[clojure.walk :as w]
;;                   [clojure.spec.alpha :as s]]
;;        :params [db entity-type name-record spec]
;;        :code
;;        (if (s/valid? spec name-record)
;;          (let [ident (keyword entity-type "id")
;;                template (-> (d/entity db [:template/describes ident])
;;                             :template/format)
;;                last-id (d/invoke db
;;                                  :wormbase.dbfns/latest-id-number
;;                                  db
;;                                  ident)
;;                identify-rec
;;                (fn [idx rec]
;;                  (let [next-identifier (format template (+ 1 idx last-id))
;;                        temp-part (keyword "db.part" entity-type)
;;                        temp-id (d/tempid temp-part)
;;                        species-lur (-> rec :gene/species vec first)]
;;                    (merge (-> (assoc rec ident next-identifier)
;;                               (assoc :gene/species species-lur))
;;                           {:db/id temp-id})))
;;                new-name (identify-rec 0 name-record)
;;                xxx (println "Returning from txor fn:" (pr-str new-name))]
;;            [new-name])
;;          (let [problems (s/explain-data spec name-record)]
;;            (println "SPEC VALIDATION FAILED!")
;;            (prn spec)
;;            (prn name-record)
;;            ;; (expound.alpha/expound spec name-record)
;;            (throw (ex-info "Not valid according to spec."
;;                            {:problems (s/explain-data spec name-record)
;;                             :type ::validation-error
;;                             ;; :spec (pr-str spec)
;;                             :valid? (s/valid? spec name-record)
;;                             :records name-record}))))})}

;;    {:db/ident :wormbase.dbfns/collate-cas-batch
;;     :db/doc "Collate a collection of Compare-and-swap operations."
;;     :db/fn
;;     (d/function
;;      '{:lang "clojure"
;;        :params [db entity name-record]
;;        :code
;;        (let [eid (:db/id entity)
;;              e-keys (keys name-record)
;;              existing (->> e-keys
;;                            (mapv (partial get entity))
;;                            (zipmap e-keys))]
;;          (->> (mapv (fn [[old-k old-v] [new-k new-v]]
;;                       (assert (= old-k new-k)
;;                               "Should never happen!")
;;                       (when (not= old-v new-v)
;;                         [:db.fn/cas eid new-k old-v new-v]))
;;                     existing name-record)))})}

;;    {:db/ident :wormbase.dbfns/update-names
;;     :db/doc "Update or add to names for given entity"
;;     :db/fn
;;     (d/function
;;      '{:lang "clojure"
;;        :requires [[clojure.walk :as w]
;;                   [clojure.spec.alpha :as s]]
;;        :params [db lur name-records spec]
;;        :code
;;        '(if (s/valid? spec name-records)
;;           (let [entity (d/entity db lur)
;;                 eid (:db/id entity)
;;                 collate-cas-batch (partial
;;                                    d/invoke
;;                                    db
;;                                    :wormbase.dbfns/collate-cas-batch
;;                                    db
;;                                    entity)
;;                 new-names (flatten (map collate-cas-batch name-records))]
;;             new-names)
;;           (throw (ex-info "Not valid according to spec."
;;                           {:problems (s/explain-data spec name-records)
;;                            :type ::validation-error
;;                            :records name-records})))})}))

;; TODO: not sure the canonical species listing should "live" here...
(def worms ["Caenorhabditis elegans"
            "Caenorhabditis briggsae"
            "Caenorhabditis remanei"
            "Caenorhabditis brenneri"
            "Pristionchus pacificus"
            "Caenorhabditis japonica"
            "Brugia malayi"
            "Onchocerca volvulus"
            "Strongyloides ratti"])

(def seed-data {:species
                (->> (map ows/latin-name->ident worms)
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
                [{:user/roles #{:user.role/admin}
                  :user/email "matthew.russell@wormbase.org"}]})


;; TODO: conformity uses `schema-ident` to uniquely identity idempotent
;;       schema transactions.
;;       find a way to version the schema by passing this in.
;;       e.g: could be release number (WS\d+), or a timestamp-ed string
(defn install [conn run-n]
  (let [schema-ident (keyword (str "schema-" run-n))]
    (let [db-fns (-> (io/resource "schema/tx-fns.edn") slurp read-string)
          db-schemas [owsb/db-specs
                      owst/db-specs
                      owsp/db-spces
                      owsu/db-specs
                      owss/db-specs
                      owsg/db-specs]
          txes-inner (spectomic/datomic-schema (apply concat db-schemas))
          ;; yyy (do (println "INNER" txes-inner)
                  ;; (prn))
          x (assoc-in {}
                      [schema-ident :txes]
                      (cons db-fns [txes-inner]))]
      ;; (println x)
      (c/ensure-conforms conn x))
    (let [seeds (->> seed-data
                     ((apply juxt (keys seed-data)))
                     (assoc-in {} [:txes]))]
      (c/ensure-conforms conn {::seed-data seeds}))))
  
