(ns wormbase.names.event-broadcast
  "Relay messages for consumption by parties interested (primarily ACeDB clients).

  DEPREACTED: This module is not currently used.
              The intent was for \"events\" perfomed via the Web UI to be queued in
              some external storage for the purpose of relaying in another database (GeneACe).
              This idea has been superceded by the GeneACe curator querying a rest endpoint periodically.

              The module here is perserved \"just in case\" we want to use it,
              but the event broadcast facility has been switched off (No events will be relayed to any storage).
  "
  (:require
   [clojure.tools.logging :as log]
   [datomic.api :as d]
   [wormbase.names.util :as wnu]
   [wormbase.names.event-broadcast.proto :as wneb]))

(defn read-changes [{:keys [db-after tx-data]}]
  (d/q '[:find ?aname ?val
         :in $ [[_ ?a ?val]]
         :where
         [?a :db/ident ?aname]]
       db-after
       tx-data))

(defn process-report
  "Process a transaction report using `event-brodcaster`.

  `include-agents` can be used to filter which events can be sent by
  the agent described in `:provenance/how`. It should be function
  accepting a single argument, and should return a boolean.

  `tx-report-queue` should be an instance of the blocking queue returned by
  `datomic.api/tx-report-queue`.

  `report` should be the element from the `tx-report-queue` queue to be processed."
  [event-broadcaster include-agents tx-report-queue report]
  (let [db-after (:db-after report)
        changes (->> report
                     read-changes
                     (into {})
                     (wnu/resolve-refs db-after))
        tx-k->db-id (->> (:tx-data report)
                         (map (fn [datom]
                                (list (d/ident db-after (.a datom)) (.e datom))))
                         (map (partial apply array-map))
                         (into {}))]
    ;; debugging:
    ;; (ownu/datom-table db-after (:tx-data report))
    (when (include-agents (:provenance/how changes))
      (log/info "Sending event message to event brodcaster.")
      (wneb/send-message event-broadcaster
                          (assoc changes
                                 :tx-id
                                 (format "0x%016x" (:db/txInstant tx-k->db-id))))
      (while (not (wneb/message-persisted? event-broadcaster changes))
        (log/info "Message not persisted in storage yet, backing-off for 5 seconds")
        ;; Perhaps terminate this loop and abort if unable to get a result?
        (Thread/sleep 5000))
      (.remove tx-report-queue report))))

(defn monitor-tx-changes
  "Monitor datomic transactions for events that are desired for later consumption
  by clients wishing to process the same update(s) in ACeDB.

  `send-changes-fn` should be a functio accepting a map of changes to be sent.
  e.g: via AWS SQS, or possibly email."
  [tx-report-queue event-broadcaster & {:keys [include-agents]
                                        :or {include-agents #{:agent/web}}}]
  (let [peek-report #(.peek tx-report-queue)]
    (loop [report (peek-report)]
      (cond
        (nil? report) (Thread/sleep 5000)
        report (process-report event-broadcaster
                               include-agents
                               tx-report-queue
                               report))
      (recur (peek-report)))))

(defn start-queue-monitor [conn event-broadcaster]
  (let [tx-report-queue (d/tx-report-queue conn)]
    (future (monitor-tx-changes tx-report-queue event-broadcaster))))

;; DISABLED.
;; (mount/defstate change-queue-monitor
;;   :start (fn []
;;            (log/info "Starting change queue monitor")
;;            (start-queue-monitor
;;             wdb/conn
;;             (-> {} wneb-s3/map->TxEventBroadcaster wneb/configure)))
;;   :stop (fn []
;;           (log/info "Stopping change queue monitor")
;;           (future-cancel change-queue-monitor)
;;           (log/info "Change queue monitor stopped")))
