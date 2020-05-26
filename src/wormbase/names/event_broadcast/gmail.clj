(ns wormbase.names.event-broadcast.gmail
  (:require
   [postal.core :as postal]
   [wormbase.names.event-broadcast.proto :as evb-proto]))

(defn derive-message-subject [event]
  (str (:event/type event)))

(defrecord TxEventBroadcaster [config]
  evb-proto/TxEventBroadcaster
  (send-message [this changes]
    (let [conn (:config this)
          subject (derive-message-subject changes)]
      (postal/send-message conn
                           {:from (:user conn)
                            :to (:group-list conn)
                            :subject subject}))))


(def make (partial evb-proto/make map->TxEventBroadcaster :gmail))

