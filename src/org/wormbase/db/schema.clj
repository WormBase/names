(ns org.wormbase.db.schema
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.spec.alpha :as s]
   [datomic.api :as d]
   [io.rkn.conformity :as c]
   [org.wormbase.species :as ows]
   [org.wormbase.specs.agent :as owsa]
   [org.wormbase.specs.biotype :as owsb]
   [org.wormbase.specs.gene :as owsg]
   [org.wormbase.specs.species :as owss]
   [org.wormbase.specs.provenance :as owsp]
   [org.wormbase.specs.template :as owst]
   [org.wormbase.specs.user :as owsu]
   [provisdom.spectomic.core :as spectomic]))

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

(def seed-data {:agents
                [{:agent/id :agent/web-form}
                 {:agent/id :agent/script}]
                :species
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
          db-schemas [owsa/db-specs
                      owsb/db-specs
                      owst/db-specs
                      owsu/db-specs
                      owsp/db-specs
                      owss/db-specs
                      owsg/db-specs]
          all-db-specs (spectomic/datomic-schema (apply concat db-schemas))
          seeds {::seed-data {:txes (-> seed-data vals vec)}}
          txes (assoc-in {}
                         [schema-ident :txes]
                         (cons db-fns [all-db-specs]))]
      ;; NOTE: datomic-schema-grapher.core/graph-datomic won't show the
      ;;       relations without some data installed.
      ;;       i.e schema alone will not draw the arrows between refs.
      ;; (println txes)
      (c/ensure-conforms conn txes)
      (c/ensure-conforms conn seeds))))

