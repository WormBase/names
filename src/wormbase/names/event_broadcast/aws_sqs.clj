(ns wormbase.names.event-broadcast.aws-sqs
  (:require
   [amazonica.aws.sqs :as sqs]
   [wormbase.names.event-broadcast.proto :as evb-proto]))

(defn- sqs-queue [config]
  (if-let [aq (-> config :queue-name sqs/find-queue)]
    aq
    (apply sqs/create-queue (-> config vec flatten))))

(defrecord TxEventBroadcaster [config]
  evb-proto/TxEventBroadcaster
  (configure [this]
    (assoc-in this [:config :queue] (sqs-queue)))
  (message-persisted? [this message-id]
    (let [msgs (:messages (sqs/receive-message
                           :queue-url (get-in this [:config :queue])
                           :delete false))]
      (not-empty (filter #(= (:message-id %) message-id) msgs))))
  (send-message [this changes]
    (sqs/send-message (get-in this [:config :queue]) changes)))

(def make (partial evb-proto/make map->TxEventBroadcaster :aws-sqs))
