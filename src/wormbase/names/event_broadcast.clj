(ns wormbase.names.event-broadcast
  "Relay messages for consumption by parties interested (primarily ACeDB clients)."
  (:require
   [datomic.api :as d]
   [mount.core :as mount]
   [wormbase.db :as owdb]
   [wormbase.names.util :as ownu]
   [wormbase.names.event-broadcast.proto :as owneb]
   [wormbase.names.event-broadcast.aws-s3 :as owneb-s3]))

(defn read-changes [{:keys [db-after tx-data] :as report}]
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
                     (ownu/resolve-refs db-after))
        tx-k->db-id (->> (:tx-data report)
                         (map (fn [datom]
                                (list (d/ident db-after (.a datom)) (.e datom))))
                         (map (partial apply array-map))
                         (into {}))]
    ;; debugging:
    ;; (ownu/datom-table db-after (:tx-data report))
    (when (include-agents (:provenance/how changes))
      (comment "LOGGING")
      (owneb/send-message event-broadcaster
                          (assoc changes
                                 :tx-id
                                 (format "0x%016x" (:db/txInstant tx-k->db-id))))
      (while (not (owneb/message-persisted? event-broadcaster changes))
        (comment "LOGGING")
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
  (let [peek-report #(.peek tx-report-queue)
        qsize #(.size tx-report-queue)]
    (loop [report (peek-report)]
      (cond
        (nil? report) (Thread/sleep 5000)
        report (process-report event-broadcaster
                               include-agents
                               tx-report-queue
                               report))
      (recur (peek-report)))))

(defn start-queue-monitor [conn event-broadcaster]
  (comment "TODO: LOGGING")
  (let [tx-report-queue (d/tx-report-queue conn)]
    (future (monitor-tx-changes tx-report-queue event-broadcaster))))

(mount/defstate change-queue-monitor
  :start (fn []
           (comment "LOGGING")
           (start-queue-monitor
            owdb/conn
            (-> {} owneb-s3/map->TxEventBroadcaster owneb/configure)))
  :stop (fn []
          (comment "LOGGING")
          (future-cancel change-queue-monitor)
          (println "Stop Queue Service")))
