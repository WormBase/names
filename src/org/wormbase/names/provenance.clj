(ns org.wormbase.names.provenance
  (:require
   [clojure.string :as str]
   [datomic.api :as d]
   [java-time :as jt]
   [org.wormbase.db :as owdb]
   [org.wormbase.names.agent :as owna]
   [org.wormbase.names.util :as ownu]))

(defn assoc-provenence [request payload what]
  (let [id-token (:identity request)
        prov (or (ownu/select-keys-with-ns payload "provenance") {})
        ;; TODO: accept :person/email OR :person/id (s/conform...)
        email (or (some-> prov :provenance/who :person/email)
                  (:email id-token))
        who (:db/id (d/entity (:db request) [:person/email email]))
        whence (get prov :provenance/when (jt/to-java-date (jt/instant)))
        how (owna/identify id-token)
        why (:provenance/why prov)
        prov {:db/id "datomic.tx"
              :provenance/what what
              :provenance/who who
              :provenance/when whence
              :provenance/how how}]
    (merge
     prov
     (when-not (str/blank? why)
       {:provenance/why why})
     (when (inst? whence)
       {:provenance/when whence}))))
