(ns wormbase.names.event-broadcast.aws-s3
  (:require
   [clojure.edn :as edn]
   [amazonica.aws.s3 :as s3]
   [wormbase.names.util :as wnu]
   [wormbase.names.event-broadcast.proto :as evb-proto]
   [clojure.string :as str])
  (:import
   (java.io ByteArrayInputStream)))

(defn derive-bucket-key [event]
  (let [parts ["name-server"
               "events"
               (name (:provenance/what event))
               (str (:tx-id event) ".edn")]]
    (str/join "/" parts)))

(defn- bucket-name [tx-event-brodcaster]
  (get-in tx-event-brodcaster [:config :bucket-name]))

(defrecord TxEventBroadcaster [config]
  evb-proto/TxEventBroadcaster
  (message-persisted? [this changes]
    (not (nil? (s3/get-object-metadata
                :bucket-name (bucket-name this)
                :key (derive-bucket-key changes)))))
  (send-message [this changes]
    (let [data (.getBytes (pr-str changes) "UTF-8")
          input-stream (ByteArrayInputStream. data)]
      (s3/put-object :bucket-name (get-in this [:config :bucket-name])
                     :key (derive-bucket-key changes)
                     :input-stream input-stream
                     :metadata {:content-length (count data)}
                     :return-values "ALL_OLD"))))

(def make (partial evb-proto/make map->TxEventBroadcaster :aws-s3))
