(ns wormbase.names.provenance
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [datomic.api :as d]
   [java-time :as jt]
   [wormbase.db :as wdb]
   [wormbase.names.agent :as wna]
   [wormbase.specs.person :as wsp]
   [wormbase.names.util :as wnu]))

(defn- person-lur-from-provenance [prov]
  (when-let [who (:provenance/who prov)]
    (if (string? who)
      (when (s/valid? ::wsp/identifier who)
        (s/conform ::wsp/identifier who))
      (first (into [] who)))))

(defn assoc-provenence [request payload what]
  (let [id-token (:identity request)
        prov (or (wnu/select-keys-with-ns payload "provenance") {})
        ;; TODO: accept :person/email OR :person/id (s/conform...)
        person-lur (or (person-lur-from-provenance prov)
                       [:person/email (:email id-token)])
        who (:db/id (d/entity (:db request) person-lur))
        whence (get prov :provenance/when (jt/to-java-date (jt/instant)))
        how (wna/identify id-token)
        why (:provenance/why prov)
        prov {:db/id "datomic.tx"
              :provenance/what what
              :provenance/who who
              :provenance/when whence
              :provenance/how how}]
    (merge
     prov
     (when (nil? how)
       {:provenance/how :agent/importer})
     (when-not (str/blank? why)
       {:provenance/why why})
     (when (inst? whence)
       {:provenance/when whence}))))
