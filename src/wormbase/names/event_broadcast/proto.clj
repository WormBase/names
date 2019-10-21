(ns wormbase.names.event-broadcast.proto
  (:require [wormbase.util :as wu]))


(defprotocol TxEventBroadcaster
  (message-persisted? [this message-id])
  (send-message [this changes])
  (configure [this]))


(defn make [ctor config-kw]
  (let [config (get-in (wu/read-app-config) [:event-broadcast config-kw])]
    (ctor {:config config})))
