(ns org.wormbase.names.ace-relay
  "Relay messages for consumption by parties interested (primarily ACeDB clients).
  Ueses Amazon Simple Queueing Service (SQS) as the message queue provider."
  (:require
   [amazonica.aws.sqs :as sqs]
   [datomic.api :as d]
   [mount.core :as mount :refer [defstate]]
   [org.wormbase.db :as owdb]
   [org.wormbase.names.util :as ownu]))

(def ace-queue-conf {:queue-name "org-wormbase-names-ace-relay_messages"
                     :attributes
                     {:VisibilityTimeout 30 ;; sec
                      :MaximumMessageSize 65536 ;; bytes
                      :MessageRetentionPeriod 3628800 ;; sec
                      :ReceiveMessageWaitTimeSeconds 10}})

;; TODO: superfluous?
;; (defmulti send-message (fn [tx] :noop))
;; (defmethod send-message :event/new-gene [tx-data])
;; (defmethod send-message :event/update-gene [tx-data])
;; (defmethod send-message :event/kill-gene [tx-data])
;; (defmethod send-message :event/split-gene [tx-data]) 
;; (defmethod send-message :event/merge-genes [tx-data])

(defn- create-queue [& args]
  ;; TODO: really call the sqs fn with args of course:
  ;; sqs/create-queue
  (println "Faking AWS SQS CREATE QUEUE")
  "urn:fake:fake:fake")

(defn ace-queue []
  (if-let [aq (-> ace-queue-conf :queue-name sqs/find-queue)]
    aq
    (apply create-queue (-> ace-queue-conf vec flatten))))

(defn read-changes [{:keys [db-after tx-data] :as report}]
  (d/q '[:find ?aname ?val
         :in $ [[_ ?a ?val]]
         :where
         [?a :db/ident ?aname]]
       db-after
       tx-data))

(defn send-changes-via-aws-sqs [queue changes]
  (sqs/send-message queue changes))

(defn monitor-tx-changes
  "Monitor datomic transactions for events that are desired for later consumption
  by clients wishing to process the same update(s) in ACeDB.

  `send-changes-fn` should be a functio accepting a map of changes to be sent.
  e.g: via AWS SQS, or possibly email."
  [tx-report-queue send-changes-fn]
  (while true
    ;; TODO: factor out fn that works on the report queue? (.take...)
    (let [report (.take tx-report-queue) ;; blocks until message is availableb
          db-after (:db-after report)
          changes (->> report
                       read-changes
                       (into {})
                       (ownu/resolve-refs db-after))]
      (comment "TODO: LOGGING")
      (println "CHANGES:" changes)
      (send-changes-fn changes))))

(defn start-queue-monitor [conn send-changes-fn]
  (comment "TODO: LOGGING")
  (println "Start Queue Service")
  (let [tx-report-queue (d/tx-report-queue conn)]
    (future (monitor-tx-changes tx-report-queue send-changes-fn))))

(mount/defstate change-queue-monitor
  :start (fn []
           (start-queue-monitor
            owdb/conn
            (partial send-changes-via-aws-sqs (ace-queue))))
  :stop (fn []
          (println "CANCELLING FUTURE")
          (future-cancel change-queue-monitor)
          (println "Stop Queue Service")))
