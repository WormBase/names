(ns org.wormbase.specs.biotype
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [miner.strgen :as sg]
   [org.wormbase.db.schema :as db-schema]
   [spec-tools.spec :as st]))

(defn vocab []
  (let [bt-keys (-> (:key-mappings db-schema/GeneBiotype)
                    (dissoc :gene.biotype :biotype)
                    (keys))
        bt-ns-kwds (->> bt-keys
                        (map name)
                        (map (partial keyword "biotype")))]
    (set bt-ns-kwds)))

(defn valid-id? [id]
  ((vocab) id))

(defprotocol BioTypeIdent
  (-convert-to-ident [value]))

(extend-protocol BioTypeIdent
  String
  (-convert-to-ident [s]
    (keyword "biotype" s))

  clojure.lang.Keyword
  (-convert-to-ident [kw]
    (if (empty? (namespace kw))
      (-convert-to-ident (name kw))
      kw)))

(defn convert-to-ident [value]
  (-convert-to-ident value))

(defn valid-short-name? [sn]
  (let [voc (vocab)
        idents (set (map convert-to-ident voc))]
    (idents (convert-to-ident sn))))


;; TODO: make these specs generatable - i.e s/exercise should work on them.
(s/def ::id (s/with-gen (s/and st/keyword? #(valid-id? %))
              #(s/gen (vocab))))

;; (s/def ::id (s/and st/keyword? #(valid-id? %)))

(s/def ::short-name
  (s/with-gen
    (s/or :biotype-string (s/and st/string? valid-short-name?)
          :biotype-keyword (s/and st/keyword? valid-short-name?))
    #(s/gen (->> (vocab)
                 (map name)
                 (map (peek (shuffle [str keyword])))
                 (set)))))
