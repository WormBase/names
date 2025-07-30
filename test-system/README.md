# wormbase.names.test-system

This package is used to reset the name-service's test-environment
to match the latest production-environment DB snapshot.

Bash instruction below assume this directory (_test-system_)
as starting-point working directory.

## Prerequisites
 * AWS (IAM) credentials set in users environment/shell
 * (IAM) User login credentials to the AWS web console
 * Appropriate AWS IAM permissions granted to AWS IAM user (ask an admin)

A clone of the production Elastic Beanstalk environment exists with the name: `wormbase-names-test`.


## Usage

To use this `wormbase.names.test-system` package, [build the jar from source](#build)
or download the latest release from `s3://wormbase/names/test-system/wb-names-test-system.jar`.

**Note:**  
Before resetting the test system, communicate this will be done to the wider group.
Upon performing a reset, **all current test-system data will be destroyed!**

1. Set the `AWS_PROFILE` and `AWS_DEFAULT_REGION` environment variables in your shell (example below for bash):
```bash
export AWS_PROFILE="<your-local-wormbase-aws-profile-name-here>"
export AWS_DEFAULT_REGION="us-east-1"
```

2. Use a `screen` or `tmux` session to run the following command:
```bash
java -cp wb-names-test-system.jar clojure.main -m wormbase.names.test-system reset
```

Further options (advanced usage, not needed for normal operations)
can be printed by using the `--help` argument like so:
```bash
java -cp wb-names-test-system.jar clojure.main -m wormbase.names.test-system --help
```

### Reset process
Running the reset command will take approximately half an hour.  
This command will perform the following operations in the WormBase AWS account:

1. Restore a backup of the production DynamoDB table for the names service to a new DynamoDB table.
    Runtime: ~15 mins

2. Create and execute a CloudFormation changeset for the test-environment transactors stack (`WBNamesTestTransactor`),
   to set the appropriate parameters (point to the correct (new) DynamoDB table).
    Runtime: ~10mins

3. Update Elastic Beanstalk `wormbase-names-test` configuration (restarts web app servers)
   to point to the new DynamoDB tables (datomic storage).
    Runtime: ~5mins

4. Once the new test-system is in place, DynamoDB tables from the previous test-system will be deleted.

### Troubleshooting
In the event of interrupting the program (e.g with `Ctrl-C` or `kill`),
the state of the test system will need to be assessed and restored to a given state.

To do this, an AWS administrator must log into the AWS console and inspect the state for each AWS service separately.
The defaults of the program as described in the `--help` report (see above) should provide
the administrator with the details required.

To reset to a state such that the reset command can work:

1. Delete the `WBNamesTestTransactor` cloud formation stack
2. Terminate the Elastic Beanstalk environment `wormbase-names-test`.
3. Delete any DynamoDB table with the prefix `WSNames-test`. Be careful not to delete `WSNames-prod` (production env table)!
4. Clone the production Elastic Beanstalk environment: `wormbase-names`
   to create a new EB test environment (`wormbase-names-test`).
5. Create a new CloudFormation transactor stack named `WBNamesTestTransactor`

The names of DynamoDB, Cloudformation and Elastic Beanstalk resources above can be changed,
but it is recommended to keep the defaults (as described in the `--help` report as described above).

Terminating an Elastic Beanstalk environment results in its name not being available for up to 2 hours.
If you should wish to proceed with a new Elastic Beanstalk environment name (e.g `wormbase-names-test2`),
then you will need to update the DNS mapping associated with https://test-names.wormbase.org in AWS Route53:
    * Go to the [console](https://console.aws.amazon.com/route53/) > hosted zones.
    * Click `wormbase.org`, on the next page search for `names` in the Records filter (search) box.
    * Edit the recordset to link test-names.wormbase.org to the new Elastic Beanstalk environment URL
      (e.g: `wormbase-names-test2.us-east1.elasticbeanstalk.com`) and save the changes.


## Build
```bash
make target/wb-names-test-system.jar
```


## Release & deployment

Ensure you have your WormBase AWS credentials set in your environment.

Edit the top-level <version> of this package in [`pom.xml`](./pom.xml), then
run:

```bash
make clean target/wb-names-test-system.jar deploy
```




