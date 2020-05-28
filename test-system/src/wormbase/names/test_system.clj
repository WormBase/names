(ns wormbase.names.test-system
  (:require
   [clojure.string :as str]
   [clojure.tools.cli :refer [parse-opts]]
   [amazonica.aws.cloudformation :as cfn]
   [amazonica.aws.dynamodbv2 :as ddb]
   [amazonica.aws.elasticbeanstalk :as eb]
   [amazonica.aws.securitytoken :as st]
   [java-time :as jt]))

(def operator-iam-spec
  {:role-arn "arn:aws:iam::357210185381:role/wb-names-test-system-operator"
   :role-session-name "wb-names-test-system-operator"})

(defn assume-credentials []
  (-> operator-iam-spec
      (assoc :role-duration-seconds 7200)
      (st/assume-role)
      :credentials))

;; General utility functions used by others.

(defn aws-username
  "Retrieve the current user's AWS username/profile via AWS STS."
  []
  (-> (st/get-caller-identity)
      :arn
      (str/split #"/")
      (last)))

(defn wait-for-status
  "Invokes a `status-check-func` function, then waits up-to
  `timeout-secs` miliseconds for a given operation described by
  `op-label` to conclude.

   `status-check-func` should be a function accepting 0 arguments,
   returning a boolean to indicate if the operation has concluded
   successfully.

   Should the wait time for `timeout-secs` be reached, then
   an exception will be thrown, terminating the procedure.
   Manual cleanup of proceeding AWS resources will be necessary."
  [status-check-func timeout-secs op-label & {:keys [interval-secs]
                                              :or {interval-secs 3}}]
  {:pre [(ifn? status-check-func)
         (int? timeout-secs)
         (pos-int? interval-secs)]}
  (when (<= timeout-secs 0)
    (throw (ex-info "Timeout exceeded waiting for "
                    {:operation op-label})))
  (when-not (status-check-func)
    (Thread/sleep (* interval-secs 1000))
    (recur status-check-func (- timeout-secs interval-secs) op-label {:interval-secs 3})))

(defn tags-for-op
  "Return a list of tags to associate with an AWS resource, specifying a `purpose`."
  [purpose]
  [{:key "CreatedBy" :value (aws-username)} 
   {:key "Purpose" :value purpose}])

;; DynamoDB operations

(defn test-table?
  "Return true iif `table-name` is a table to be used by the test system."
  [table-name test-table-name-prefix]
  (str/starts-with? table-name test-table-name-prefix))

(defn ddb-list-tables
  [creds test-table-name-prefix]
  (let [all-tables (-> creds (ddb/list-tables) :table-names)]
    (filter #(test-table? % test-table-name-prefix)
            all-tables)))

(defn next-table-name
  "Return a table name for use by the test system.

  This implementation employs a number incrementor."
  [test-tables test-table-name-prefix]
  (let [latest-tt (last test-tables)
        ln (re-find #"\d+" (or latest-tt ""))
        sn (if ln
             (inc (read-string ln))
             0)]
    (str test-table-name-prefix "-" (str sn))))

(defn tables-to-delete [new-table-name table-names table-name-prefix]
  (disj (set (filter #(test-table? % table-name-prefix) table-names))
        new-table-name))

(defn ddb-table-status
  [credentials ddb-table-name]
  (when ((-> credentials ddb/list-tables :table-names set) ddb-table-name)
    (->  credentials
         (ddb/describe-table ddb-table-name)
         :table
         :table-status)))

(defn ddb-table-restoring?
  [credentials ddb-table-name]
  (= "RESTORING" (ddb-table-status credentials ddb-table-name)))

(defn ddb-table-restored?
  "Return true iif the DyanmoDB table has been restored."
  [credentials ddb-table-name]
  (= "ACTIVE" (ddb-table-status credentials ddb-table-name)))

(defn ddb-restore-to-test-table
  [credentials source-table-name target-table-name]
  (if (ddb-table-restoring? credentials target-table-name)
    (do
      (println "DynamoDB table " target-table-name " is being restored")
      (println "Please wait."))
    (ddb/restore-table-to-point-in-time
     credentials
     {:source-table-name source-table-name
      :target-table-name target-table-name
      :use-latest-restorable-time true}))
  (wait-for-status
   (partial ddb-table-restored? credentials target-table-name)
   1200
   "Restoring DynamoDB Table from PITR backup of live Names DB."))

(defn remove-old-test-tables
  [test-tables current-test-table]
  (let [creds (assume-credentials)]
    (doseq [table-name (disj (set test-tables) current-test-table)]
      (ddb/delete-table creds {:table-name table-name}))))

;; CloudFormation operations

(defn cfn-stack-status
  [creds stack-name]
  (some-> (cfn/describe-stack-events creds :stack-name stack-name)
          (:stack-events)
          (first)
          (:resource-status)))

(defn cfn-stack-updating?
  [creds stack-name]
  (= (cfn-stack-status creds stack-name) "UPDATE_IN_PROGRESS"))

(defn change-set-name [& ss]
  (-> (str/join "-" (conj ss "test-system"))
      (str/lower-case)
      (str "-" (jt/to-millis-from-epoch (jt/system-clock)))))

(defn change-set-ready?
  [credentials {:keys [stack-name change-set-name]}]
  (let [ok-statuses #{"CREATE_COMPLETE" "AVAILABLE"}]
    (some->> (:summaries (cfn/list-change-sets credentials
                                               {:stack-name stack-name}))
             (filter #(and (= (:change-set-name %) change-set-name)
                           (ok-statuses (:status %))))
             (last))))

(defn update-datomic-transactors!
  "Update the datomic transactors configured via CloudFormation stack `stack-name` to use
   a new DynamoDBTable `ddb-table-name`."
  [creds stack-name ddb-table-name]
  (let [params (->> (:parameters (cfn/get-template-summary
                                  creds
                                  :stack-name stack-name))
                    (map #(select-keys % [:parameter-key :parameter-value]))
                    (map (fn [param]
                           (if (= (:parameter-key param) "DDBTableName")
                             (assoc param :parameter-value ddb-table-name)
                             (assoc param :use-previous-value true)))))
        cs-name (change-set-name ddb-table-name)]
    (cfn/create-change-set
     creds
     {:stack-name stack-name
      :change-set-name cs-name
      :use-previous-template true
      :parameters params
      :tags (tags-for-op "test system reset")})
    (wait-for-status (partial change-set-ready?
                              creds
                              {:stack-name stack-name
                               :change-set-name cs-name})
                     300
                     "Waiting for CFN change set to become available.")
    (cfn/execute-change-set creds
                            {:stack-name stack-name
                             :change-set-name cs-name})))

(defn cfn-stack-ready?
  "Return true iif the datomic tranasctors CloudFormation stack is ready to use."
  [creds stack-name]
  (= (cfn-stack-status creds stack-name) "UPDATE_COMPLETE"))

(defn cfn-update-stack
  [stack-name ddb-table-name]
  (let [creds (assume-credentials)]
    (if (cfn-stack-updating? creds stack-name)
      (println "Datomic Transactor CFN stack is updating, please wait.")
      (update-datomic-transactors! creds stack-name ddb-table-name))
    (wait-for-status (partial cfn-stack-ready? creds stack-name)
                     1200
                     "Updating Datomic Transactors.")))

;; Elastic Beanstalk operations

(defn eb-env-status
  [creds eb-env-name]
  (select-keys
   (->> (:environments (eb/describe-environments creds))
        (filter #(= (:environment-name %) eb-env-name))
        (first))
   [:health :status]))

(defn eb-env-ready? [creds eb-env-name]
  (let [{health :health status :status} (eb-env-status creds eb-env-name)]
    (and (= health "Green") (= status "Ready"))))

(defn eb-env-update-config
  "Update the configuration for an ElasticBeanstalk environment `eb-env-name` to use
   a new DynamoDB table `ddb-table`."
  [eb-env-name ddb-table]
  (let [creds (assume-credentials)
        datomic-uri (str "WB_DB_URI=datomic:ddb://us-east-1/"
                                 ddb-table
                                 "/wormbase")
        option-settings [{:option-name "EnvironmentVariables",
                          :namespace "aws:cloudformation:template:parameter",
                          :value (str datomic-uri ",_JAVA_OPTIONS=-Xmx4g")}]]
    (eb/update-environment creds
                           {:environment-name eb-env-name
                            :option-settings option-settings})
    (wait-for-status
     (partial eb-env-ready? creds eb-env-name)
     1200
     "Updating ElasticBeanStalk configuration for staging web app.")))

(defn print-status-summary
  [table-name stack-name eb-env-name]
  (let [creds (assume-credentials)
        lines [(str/join " " ["DynaoDB table: "
                              table-name
                              "->"
                              (ddb-table-status creds table-name)])
               (str/join " " ["CloudFormation Transactor stack:"
                              stack-name
                              "->"
                              (cfn-stack-status creds stack-name)])
               (str/join " " ["Elastic Beanstalk environment:"
                              eb-env-name
                              "->"
                              (:status (eb-env-status creds
                                                      eb-env-name))])]]
    (doseq [line lines]
      (println line))))

(defn reset-test-system!
  "Reset the test system to use the latest DynamoDB database table from the live names service."
  [opts]
  (let [credentials (assume-credentials)
        {source-table-name :source-table-name
         test-table-name-prefix :test-table-name-prefix
         cfn-stack-name :cfn-stack-name
         eb-env-name :eb-env-name} opts
        test-tables (ddb-list-tables credentials test-table-name-prefix)
        target-table-name (next-table-name test-tables
                                           test-table-name-prefix)]
    (print-status-summary target-table-name cfn-stack-name eb-env-name)
    (print "Resetting test system, please wait...")
    (ddb-restore-to-test-table credentials
                               source-table-name
                               target-table-name)
    (cfn-update-stack cfn-stack-name target-table-name)
    (eb-env-update-config eb-env-name target-table-name)
    (remove-old-test-tables test-tables target-table-name)
    (println "Done.")
    (print-status-summary target-table-name cfn-stack-name eb-env-name)))

(defn exit
  [status msg]
  (println msg)
  (System/exit status))

(defn usage
  [options-summary]
  (println (str/join "\n"
                     [""
                      "Usage: program-name [options] action"
                      "Options:"
                      options-summary
                      "Actions:"
                      "  reset      - reset the test system."])))

(def cli-options
  [["-h" "--help"]
   ["-s"
    "--cfn-stack-name CLOUD_FORMATION_STACK_NAME"
    :default "WBNamesTestTransactor"]
   ["-t"
    "--ddb-table-name SOURCE_DDB_TABLE_NAME"
    :default "WSNames"]
   [nil
    "--ddb-table-name-prefix PREFIX"
    :default "WSNames-test"]
   ["-e"
    "--eb-env-name ELASTIC_BEANSTALK_ENVIRONMENT_NAME"
    :default "wormbase-names-test"]])

(defn -main
  "Reset the names test system to use the latest available PITR snapshot from the live service."
  [& args]
  (let [{:keys [options errors summary arguments]} (parse-opts args
                                                               cli-options)
        {test-table-name-prefix :ddb-table-name-prefix
         src-table-name :ddb-table-name
         cfn-stack-name :cfn-stack-name
         eb-env-name :eb-env-name} options
        action (first arguments)]
    (cond
      errors (exit 1 (str/join errors "\n"))
      (:help options) (usage summary)
      (nil? action) (exit 1 "Missing required argument \"action\"")
      :else
      (case action
        (= "reset")
        (reset-test-system! {:source-table-name src-table-name
                             :test-table-name-prefix test-table-name-prefix
                             :cfn-stack-name cfn-stack-name
                             :eb-env-name eb-env-name})))))

