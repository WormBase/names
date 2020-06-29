# wormbase-names

This repository provides:
 - A web app that facilitates the sharing of identifiers and names of a subset of WormBase data types.
 - A shared library (functions used within the datomic transactor, and the web app)
 - A command line application to export the data from the names service.

The web app comprises:

 - A REST API for manipulating WormBase data types
   - Recording new entities, updating, changing entity status and more.
 - A Web interface, providing forms to perform operations via the REST API.
 - A library `wormbase.ids` that is used by the REST API to perform atomic
   identifier operations within a datomic transactor process.
 - Schema and database related functions
   The schema and related database functions are intended to evolve to
   eventually become the "central" database within the WormBase
   architecture.
 - A basic user interface to interact with the name-service.
 - Serialisation of events to a queueing system, such that those
   events can be "replayed" into various ACeDB databases.
 - User authentication (agianst the wormbase.org google organisation)

With every write operation, this names service provides provenance (who, when, where and why),
which is modelled as attributes on the "transaction entity".

## Development

### Coding style
The coding style for this project tries to adhere to both the [Clojure style guide][16] and [how to ns][17], both of which are supported by the source code linter [clj-kondo][18].

To run the source coder linter:

```bash
for d in src test; do
  clj -A:clj-kondo --lint $d
done
```

### Setup

Ensure you've installed the following software on your system:

[clojure CLI_tools][4]

[nvm][7]

[docker][8]

[awscli][9]

[awsebcli][10]

#### Docker credentials

The Makefile target `ecr-login` command will, by default, store the
authentication token un-encrypted in the file: `~/.docker/config.json`.

There is a plugin that can be used to use a store to save these tokens encrypted,
but varies depending on operating system.

For linux, there's [docker-credential-pass][12] and [pass][13], which can be used together,
which uses a GPG2 key to encrypt tokens.


### Setup client app
Setup client app **either by [making a production build of the client app](#building-the-client-app) or running a client development server**, as show here:

- First, ensure `client/package.json` has proxy configured to point at the backend API.

- Then, run:
```bash
cd client/
nvm use # optionally `nvm install` to install the latest compatible version of node.js
npm install
npm run start
```
  - This will start service serving the client assets on port 3000,
  the server should be started with the `PORT` environment variable set to *4010*.

- Finally, ensure the authentication callback URL at Google Cloud Console is configured to match the client development server configuration.

Notes:
- **Node.js and NPM***
  - This client requires compatible versions of node.js and NPM, as specified in the `engines` property [package.json](client/package.json). The easiest way to use the right version of node.js and NPM, is through the [Node Version Manage (nvm)][7].
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
	- `client/src` primarily consists of `containers` (React components involving business logic) `components/elements` (React components involving only appearance and/or UI logic).

### Run the application locally
Run with:

```bash
export WB_DB_URI="[datomic-uri]"
```

(An example of `[datomic-uri]` may be `datomic:mem://localhost:4334/names`. No transactor setup is needed for this in-memory database URI. For a persistent database, a transactor needs to be configured, in which case, the `[datomic-uri]` is based the your transactor configuration and database name.)

Run with `make run-dev-webserver PORT=[port] WB_DB_URI=[datomic-uri]`.

To allow the UI webpackDevServer to proxy to the ring server, the ring server has to be run at the host and port configured in the `"proxy"` section in [client/package.json](client/package.json).


### Tools

#### Running a Clojure REPL

Examples

 Emacs + CIDER :
 ```bash
  # Example. `:mvn/version` of nrepl changes frequently, CIDER/emacs will prompt when upgrade required.
clj -A:datomic-pro:webassets:dev -Sdeps '{:deps {cider/cider-nrepl {:mvn/version "0.23.0"}}}' -m nrepl.cmdline --middleware "[cider.nrepl/cider-middleware]"
 ```

 "Vanilla" REPL:
 ```bash
 clj -A:datomic-pro:webassets:dev -m nrepl.cmdline
 ```

From time to time it is good to check for outdated dependencies.
This can be done via the following command:
```
clj -A:outdated
```


### Testing
Use built-in testing utilities as provided by your environment, else use the `make` command
below to run all tests.
Ensure to run all tests and check they pass before submitting new pull requests.

```bash
make run-tests
```

## Releases

Releasing is a 4 step process:

 1. Release code - revision, push. creates `resources/meta.edn` that's included in the build artefacts).
 2. Build application and deploy in the AWS Elastic Container Registry (ECR).
 3. Deploy the application in AWS ElasticBeanstalk.


### Commands
```bash
# Build the client application to check it works.
make ui-build

# specify $LEVEL as one of <major|minor|patch>
make vc-release LEVEL=$LEVEL

git push --follow-tags

# print the version being deployed
make show-version

# Update <verison> in pom.xml to match.
$EDITOR pom.xml

# Build the application and deploy the docker image to the AWS Elastic Container Registry (ECR)
make release

# Deploy the application to the selected ElasticBeanStalk environmnent
# The environment that will deployed to will be marked by
# an asterisk in the output of the command:
# > eb list
# use: eb use <env-name> to change this.

eb deploy
```

## Client application

### Development
The Reach (Javascript) client application can be run using:
```bash
cd ./client
nvm use
npm run start
```
This will start service serving the client assets on port 3000,
the server should be started with the `PORT` environment variable set to *4010*.

### Building
```bash
cd client
nvm use
npm ci   # a clean install of dependencies based on package-lock.json
npm run build
```

For a full list of tasks, type:

```bash
make help
```

### Importing from GeneACe export data

Conventionally, the export files have been named in the form: `DDMMYYY_<topic>`,
and we give the datomic database a corresponding name.

The best way to run the imports is against a local `datomic:ddb-local` or `datomic:dev` transactor.

e.g: dynamodb-local

export WB_DB_URI="datmomic:ddb-local://localhost:8000/WSNames/12022019 # The Dynamo DB table here is `WSNames`

See [here][14] for creating a local DynamoDB database.

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

From a fresh database install, enter the following from a REPL session
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
The primary function of the export is for reconcilation of the datomic
names db against an ACeDB database.
The IDs, names and status of each entity in the database are output as CSV.

A jar file is deployed to the WormBase S3 account for convenience.

```bash
# export genes
java -cp <path-to-worrmbase-names-export.jar> clojure.main -m wormbase.names.export genes  /tmp/genes.csv

# export variations
java -cp <path-to-worrmbase-names-export.jar> clojure.main -m wormbase.names.export variations  /tmp/variations.csv
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

[1]: https://github.com/rkneufeld/conformity
[2]: https://clojure.org/community/downloads
[3]: https://clojure.org/about/spec
[4]: https://clojure.org/guides/getting_started
[6]: https://nodejs.org/en/
[7]: https://github.com/nvm-sh/nvm
[8]: https://docs.docker.com/install/
[9]: https://docs.aws.amazon.com/cli/latest/userguide/installing.html
[10]: https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/eb-cli3-install.html
[11]: https://github.com/facebook/create-react-app
[12]: https://github.com/docker/docker-credential-helpers/releases
[13]: https://github.com/docker/docker-credential-helpers/issues/102
[14]: https://github.com/WormBase/wormbase-architecture/wiki/Simulating-Production-Datomic-Database-with-local-storage-and-transactor
[15]: https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/eb3-deploy.html
[16]: https://github.com/bbatsov/clojure-style-guide
[17]: https://stuartsierra.com/2016/clojure-how-to-ns.html
[18]: https://github.com/borkdude/clj-kondo
