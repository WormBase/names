(ns wormbase.names.event-broadcast.proto
  (:require [wormbase.names.util :as ownu]))


(defprotocol TxEventBroadcaster
  (message-persisted? [this message-id])
  (send-message [this changes])
  (configure [this]))


(defn make [ctor config-kw]
  (let [config (get-in (ownu/read-app-config) [:event-broadcast config-kw])]
    (ctor {:config config})))
