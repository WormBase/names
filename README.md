# wormbase-names

The main functionality of this repository is to provide a web service that
facilitates the sharing of identifiers and names of a subset of WormBase data types.  
The web service comprises of:
 - A REST API (+ swagger documentation) for reading and manipulating data from the service (full CRUD support).
   Main code to be found [here](./src/wormbase).
 - [A Web interface](./client), providing forms to perform operations via the REST API.

This repository also contains:
 - [A clojure library](./ids) `wormbase.ids` that is used by the REST API to perform atomic
   (identifier) operations within a Datomic transactor process.
 - A [command line application](./export) to export the data from the names service.
 - A [clojure package](./test-system) to export the data from the names service.
   For more info, see the [README](./test-system/README.md).

More general code features are:
 - User authentication (against the wormbase.org google organisation)
 - Provenance provisioning (who, when, where and why) with every write operation,
   modelled as attributes on the "transaction entity" in Datomic.
 - Schema and database related functions  
   The schema and related database functions are intended to evolve to
   eventually become the "central" database within the WormBase
   architecture.
 - Serialisation of events to a queueing system, such that those
   events can be "replayed" into various ACeDB databases.

## Development

### Coding style
The coding style for all clojure code in this project tries to adhere to both the [Clojure style guide][16]
 and [how to ns][17], both of which are supported by the source code linter [clj-kondo][18].

To run the source coder linter:

```bash
for d in src test; do
  clj -A:clj-kondo --lint $d
done
```

### Requirements

Ensure you've installed the following software on your system to enable local building, testing and troubleshooting:
* [clojure CLI_tools][4]
* [datomic (on-prem) Pro (or pro starter)](https://my.datomic.com/downloads/pro): My datomic account required.
  Installation requires `mvn` (ubuntu package `maven`) to be installed.
* [nvm][7]
* [docker][8]
* [awscli][9]
* [build-essential (ubuntu)][19] or similar package containing `make`

#### Docker credentials

The [Makefile](./Makefile) target `ecr-login` command will, by default, store the
authentication token un-encrypted in the file: `~/.docker/config.json`.

There is a plugin that can be used to save these tokens encrypted in a store,
but varies depending on operating system.

For linux, there's [docker-credential-pass][12] and [pass][13], which can be used together,
which uses a GPG2 key to encrypt tokens.

#### Google API secrets
Before being able to run `make run-tests` (and possibly some other NS functionality as well),
you need to store the Name Service application's Google Oauth 2.0 Client ID and secret in a file on your local system.
This file is not (and should **never** be!) versioned in git or pushed to github, as doing so would create a security vulnerability.
To intantiate these file locally:
1. change your working directory to your local repository clone directory
2. Execute the following commands:
   ```bash
   install -d resources/secrets -m 700
   install -m 700 /dev/null resources/secrets/wb-ns-google-web.edn
   ```

3. Paste the following content in the above created file:
   ```clojure
   {
     :client-id ""
     :client-secret ""
   }
   ```

4. Go to the [wormbase-names-service google console credentials page](https://console.cloud.google.com/apis/credentials?project=wormbase-names-service).

5. Under OAuth 2.0 Client IDs, click on "WormBase Names Service (Web - Dev)"
	From the right-hand side of the page, copy the "Client ID" and the "Client Secret" in the appropriate strings in `resources/secrets/wb-ns-google-web.edn`. The AWS deployments (both stage and production) use the `WormBase Names Service (Web - Prod)` application details.

### REST API
To be able to run the REST API locally, define the (local) datomic DB URI as the env variable `WB_DB_URI`,
and the URI to use during Google authentication as the env variable `GOOGLE_REDIRECT_URI`.

An example of a valid datomic URI may be `datomic:mem://localhost:4334/names`. No transactor setup is needed for this in-memory database URI.
For a persistent database (like `ddb-local`), a transactor needs to be configured, in which case the `WB_DB_URI` is based on your transactor configuration and database name. Make sure to define the `DATOMIC_EXT_CLASSPATH` env variable to point to the wormbase/ids jar when setting up the transactor (see [these instruction](./ids/README.md#Build) to build the ids jar).

```bash
export DATOMIC_EXT_CLASSPATH="$HOME/git/wormbase-names/ids/target/wbids.jar"
```

When using a `ddb-local` transactor, ensure to have set AWS environment variables with mock credentials,
then run the following command to launch the local REST API service:
```bash

make run-dev-webserver PORT=[port] WB_DB_URI=[datomic-uri] GOOGLE_REDIRECT_URI="http://lvh.me:3000"
```

To allow the UI webpackDevServer to proxy to the ring server, the ring server has to be run at the host and port configured in the `"proxy"` section in [client/package.json](client/package.json) (standardly 4010 is used).

#### Tools

##### Running a Clojure REPL

Examples

  - Emacs + CIDER :
```bash
# Example. `:mvn/version` of nrepl changes frequently, CIDER/emacs will prompt when upgrade required.
clj -A:datomic-pro:webassets:dev -Sdeps '{:deps {cider/cider-nrepl {:mvn/version "0.23.0"}}}' -m nrepl.cmdline --middleware "[cider.nrepl/ cider-middleware]"
```

  - "Vanilla" REPL:
```bash
clj -A:datomic-pro:webassets:dev -m nrepl.cmdline
```

From time to time it is good to check for outdated dependencies.
This can be done via the following command:
```bash
clj -A:outdated
```

### Client app (web interface)
Correct functionality of the client app can be tested in two ways:
- Running a client development server (during development), to test individual functionality. See [instructions below](#local-rest).
- Making a production build of the client app. Failure during this process means fixes will be needed before deployment.
```bash
# performs a npm clean install of dependencies based on package-lock.json
make ui-build
```

To start up a local client development server:<a id="local-rest"></a>
1. Ensure the back-end application is running and an API endpoint is available locally (see [above](#REST-API))

2. Ensure `client/package.json` has proxy configured to point at the backend API, at the correct port (default 4010).

3. Run (bash):
```bash
cd client/
nvm use # optionally `nvm install` to install the latest compatible version of node.js
npm install
npm run start
```
  - This will start service serving the client assets on port 3000.

4. Finally, ensure the authentication callback URL at [Google Cloud Console](https://console.developers.google.com/apis/credentials?project=wormbase-names-service&folder=&supportedpurview=project) is configured to match the client development server configuration. Under OAuth 2.0 Client IDs, click _"WormBase Names Service (Web)"_ and have a look at the _"Authorized JavaScript origins"_ section.

Notes:
- **Node.js and NPM**
  - This client requires compatible versions of node.js and NPM, as specified in the `engines` property [package.json](client/package.json). The easiest way to use the right version of node.js and NPM, is through the [Node Version Manager (nvm)][7].
  - To invoke `nvm use` automatically, setup Deeper Shell Integration by following the nvm documentation.
- **Create-React-App**
  - `client/` is bootstrapped with [create-react-app][11], where you can find out more about its setup and capability
- **Port:**
	- To run the client on a different port:
```bash
PORT=[PORT] npm run start
```
- **Dependencies:**
	- Most errors about missing dependencies can be resolved with `npm install`, which installs dependencies into the `./node_modules` directory. It's safe to delete the content of `./node_modules` and/or re-run `npm install`.
	- Be sure to checking in changes in `package-lock.json`, which specifies the exact versions of npm packages installed, and allows package installation to happen in a reproducible way based on `package-lock.json` with `npm ci`.
- **Mock:**
	- Ajax calls through `mockFetchOrNot` function allows one to provide a mock implementation of an API call, in addition to the native API call.
	- Whether the mock implementation or the native implementation is invoked is determined by the 3rd argument (`shouldMock`) passed to mockFetchOrNot function.
	- `shouldMock` defaults to the `REACT_APP_SHOULD_MOCK` environment variable, when it's not passed in as an argument.
- **Directory structure**
	- [create-react-app][11] is responsible for the directory structure of `client/` except `client/src`, and relies it staying this way.
	- `client/src` primarily consists of
		- `containers`: React components involving business logic
		- `components/elements`: React components involving only appearance and/or UI logic

## Testing
Use built-in testing utilities as provided by your environment, else use the `make` command
below to run all tests.
Ensure to run all tests and check they pass before committing large code changes,
before submitting new pull requests and before deploying to any live AWS environment (test or production).

```bash
make run-tests
```

## Release & deployment
As described in [the intro](#wormbase-names), the name service exists of several components,
for which release versioning and deployment steps differ:
- Main application (REST API + web client)
  - Versioned through the repository git tags
  - Deployed through AWS EB (& ECR)
- IDs clojure library
  - Manually versioned through the `ids/pom.xml` file (and clojars)
  - Library deployed to [clojars](https://clojars.org/wormbase/ids/) (thin jar)
  - Datomic transactors (which use this library) deployed through AWS CloudFormation
- Export package
  - Not versioned
  - Deployed to S3 (uber jar)

When release & deployment is required to both the IDs library and the main application,
the correct order of deployment is to deploy the IDs library first, then update the transactors
and lastly the main application.

### Requirements

Ensure you've installed the following software on your system to enable building, testing and deployment:
* [clojure CLI_tools][4]
* [nvm][7]
* [docker][8]
* [awscli][9]
* [awsebcli][10]

### Deploying the application (REST API + client) <a id="deploying-application"></a>

#### First time setup
Before being able to deploy for the first time (after creating a new local clone of the repository),
a local EB environment must be configured.

The `--profile` is optional, but saves a default profile, which prevents
you from having to provide your profile name as input argument or
bash environment variable on every EB operation (if it's not "default").
```bash
eb init [--profile <aws-profile-name>]
```
This command will interactively walk you through saving some EB configurations in the
`.elasticbeanstalk` directory. Provide the following parameters when asked for:
* Default region: `us-east-1`
* Application to use: `names`
* Default environment: `wormbase-names-test` (this prevents accidental deployement to the production environment)
* CodeCommit?: `N`
	

#### Update release & deployment
Deploying the main application is a 3 step process:
 1. Release code - revision, push.
 2. Build application and deploy in the AWS Elastic Container Registry (ECR).
 3. Deploy the application in AWS ElasticBeanstalk.

The release and deployment process heavily uses `make` for its automation.
For a full list of all available `make` commands, type:
```bash
make help
```

To deploy an update for the main application, change your working dir
to the repository root dir and execute the following commands (bash):
```bash
# Build the client application to ensure no errors occur.
make ui-build

# Generate/update the pom.xml file (not version-controlled)
clj -Spom

# Specify $LEVEL as one of <major|minor|patch>.
# This will bump the x, y or z version number.
# SLF4J messages can be ignored (warnings, not errors).
# Clashing jar warnings can be ignored.
make vc-release LEVEL=$LEVEL

# print the version being deployed and confirm it's correctness (e.g. prevent DIRTY deployments to production)
make show-version

# Once confirmed to be correct, push the created tag to github
git push --follow-tags

# Update the pom.xml to
#   * match the version reported by make as <version> tag value
#   * have "wormbase" (unquoted) as <groupId> tag value
#   * have "names" (unquoted) as <artifactId> tag value
$EDITOR pom.xml

# Before building the application, ensure docker (daemon) is running.
# If not, start it. On Ubuntu you can do so with the following cmd:
sudo service docker start

# Build the application and deploy the docker image to the AWS Elastic Container Registry (ECR)
# NOTE: To deploy a tagged or branched codeversion that does not equal your (potentially dirty) working-dir content,
#       use the additional argument REF_NAME=<ref-name>
#       E.g. make release AWS_PROFILE=wormbase REF_NAME=wormbase-names-1.4.7
make release [AWS_PROFILE=<profile_name>]

# Deploy the application to an EB environmnent.
# Before execution:
# * Ensure to specify the correct EB environment name, in order to prevent
#   accidental deployments to the production environment!
# * Check if the hard-coded WB_DB_URI default (see MakeFile) applies.
#   If not, define WB_DB_URI to point to the appropriate datomic DB.
# * Ensure to define the correct GOOGLE_REDIRECT_URI for google authentication (http://lvh.me:3000 when developing locally)
make eb-deploy PROJ_NAME=<env-name> [GOOGLE_REDIRECT_URI=<google-redirect-uri>] [WB_DB_URI=<datomic-db-uri>] [AWS_EB_PROFILE=<profile_name>]
```

### Deploying the IDs library
For instruction about developing, building and deploying the IDs library sub-project, see the sub-project's [README](./ids/README.md).

## Other tasks

### Importing GeneACe export data in datomic DB

Conventionally, the export files have been named in the form: `DDMMYYY_<topic>`,
and we give the datomic database a corresponding name.

The best way to run the imports is against a local `datomic:ddb-local` or `datomic:dev` transactor.

e.g: dynamodb-local
```bash
export WB_DB_URI="datomic:ddb-local://localhost:8000/WSNames/12022019 # The Dynamo DB table here is `WSNames`
```

See [here][14] for instructions on creating a local DynamoDB database.

#### Import genes

This import pipeline takes two files:
  - <current_status_tsv> : A file containing the columns: WBGeneID, Species, Status, CGC Name, Sequence Name, Biotype.
  - <actions_tsv>: A file containing WBGeneID, WBPersonID, Timestamp, Event Type


```bash
clojure -A:dev:datomic-pro -m wormbase.names.importer \
 gene <current_status_tsv> <actions.tsv>
```
At time of writing (as of WormBase release WS270), the gene import pipeline takes ~5 hours to run.

#### Import variations

The variations export data is provided in a single file (no provenance is attached).

```bash
clojure -A:dev:datomic-pro -m wormbase.names.importer variation <variations_tsv>
```

At time of writing (as of WormBase release WS270), the variations
import pipeline takes ~5 mins to run.


### Import Sequence Features

We do not attempt to replay all Sequence features from an export,
and instead just record the latest ID and status.

From a fresh database install, enter the following in a REPL session
after exporting the `WB_DB_URI` environment variable appropriately:

```clojure
(require '[environ.core :refer [env]])
(require '[datomic.api :as d])
(def conn (d/connect (:wb-db-uri conn)))
@(d/transact conn [{:sequence-feature/id "<latest-id>", :sequence-feature/status :sequence-feature.status/live}
				  {:db/id "datomic.tx", :provenance/why "Initial import", :provenance/who [:person/id "YourWBPersonID"]}])
```

### Restoring the datomic database to AWS

Creation of a new remote DynamoDB database should be done via the AWS CLI or web console (outside of the scope of this document).

Follow the "standard" backup-and-restore method, for example:

```bash
mkdir $HOME/names-db-backups
cd ~/datomic-pro/datomic-pro-0.9.5703
bin/datomic backup-db $LOCAL_DATOMIC_URI file://$HOME/names-db-backups/names-db
```

Before restoring the database:
 - Make a note of the current value of `write-capacity`
 - Increase the `write-capacity` of the DDB table via the AWS CLI/web
   console to be 1000 (or more), then run the restore command shown
   below.

```bash
bin/datomic restore-db file://$HOME/names-db-backups/names-db $REMOTE_DATOMIC_URI
```
After the process concludes, restore the `write-capacity` back to its original value.

Ensure to configure the application via the `.ebextensions/app-env.config` file to match $REMOTE_DATOMIC_URI.
After deploying a release, verify that the URI has changed in the ElasticBeanStalk configuration section.

### Exporting names data to CSV
The primary function of the export output is for reconcilation of the datomic
names db against an ACeDB database.  
The IDs, names and status of each entity in the database are output as CSV.

A jar file is deployed to the WormBase S3 bucket (`s3://wormbase/names/exporter/wb-names-export.jar`) for convenience.

```bash
# export genes
java -cp <path-to-wormbase-names-export.jar> clojure.main -m wormbase.names.export genes /tmp/genes.csv

# export variations
java -cp <path-to-wormbase-names-export.jar> clojure.main -m wormbase.names.export variations /tmp/variations.csv
```

The exporter can also be run from a checkout of this repository:

```bash
cd <wormbase-names_checkout>/export

# export genes
clojure -A:dev:datomic-pro -m wormbase.names.export genes  /tmp/genes.csv

# export variations
clojure -A:dev:datomic-pro -m wormbase.names.export variations  /tmp/variations.csv
```

## License
EPL (Eclipse Public License)  
Copyright ©  WormBase 2018, 2019

[4]: https://clojure.org/guides/getting_started
[7]: https://github.com/nvm-sh/nvm
[8]: https://docs.docker.com/install/
[9]: https://docs.aws.amazon.com/cli/latest/userguide/installing.html
[10]: https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/eb-cli3-install.html
[11]: https://github.com/facebook/create-react-app
[12]: https://github.com/docker/docker-credential-helpers/releases
[13]: https://github.com/docker/docker-credential-helpers/issues/102
[14]: https://github.com/WormBase/wormbase-architecture/blob/develop/docs/Simulating-Production-Datomic-Database-with-local-storage-and-transactor.md
[16]: https://github.com/bbatsov/clojure-style-guide
[17]: https://stuartsierra.com/2016/clojure-how-to-ns.html
[18]: https://github.com/borkdude/clj-kondo
[19]: https://packages.ubuntu.com/search?keywords=build-essential&searchon=names