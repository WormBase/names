# wormbase-names

Facilitate the sharing of identifiers and names of a subset of
WormBase data types.

ACeDB does not support concurrent writes, so a brokering "name-server"
service has historically been required to coordinate curation activity.

This library intends to provide:

 - A rest API service upon which a web app can be written to replace
   the existing "name server" hosted at the Sanger institute.

 - Schema and database related functions
   The schema and related database functions are intended to evolve to
   eventually become the "central" database within the WormBase
   architecture.

 - A basic user interface to interact with the name-service.

 - Serialisation of events to a queueing system, such that those
   events can be "replayed" into various ACeDB databases.

 - User authentication (agianst the wormbase.org google organisation)

## Datomic schema design/approach
Schema is defined as vanilla datomic schema entities in EDN, which is
read in upon starting the web-service, idemopotently using
[conformity][1].

Provenance (who, when, where and why) is
modelled as attributes on transactions.

The excewption being that a data-types' status (dead / live /
supressed) is modelled on the entity, not the transaction.

The latest alpha version of [clojure][2] is used to facilitate the use
of [clojure.spec][3] to provide validation and test-data-generation for
testing.ri

## The existing WormBase "name server" - background
The current wormbase "name service", hosted at the Sanger Institute,
is a perl cgi webapp, backed by a MySQL database.  It exists to
facilitate pan-institution sharing of WormBase identifiers and names
for `Gene`, `Feature` and `Variation` entities (ACeDB does not support
concurrent write-access).

## Development

## Setup

```bash
cd /tmp
lein upgrade
```
Ensure you've installed the following software on your system:

[clojure 1.9][4]

[leineningen 2.8.1+][5]

[nodejs][6]

[yarn][7]

[docker][8]

[awscli][9]

[awsebcli][10]

### Setup client app
Setup client app either by [making a production build of the client app](#building-the-client-app) or running a client development server:

- First, ensure `client/package.json` has proxy configured to point at the backend API.

- Then, run:
```bash
cd client/
yarn install
yarn run start
```


### Run the application locally
Run with:

```bash
export WB_DB_URI="<datomic-uri>"
```

Run with `lein ring server` or `lein ring server-headless`.


### Testing
Use built-in testing utilities as provided by leiningen.
Please run before committing/submitting new pull requests.

```bash
lein test
```

```bash
lein do clean, test-refresh
```

## Production deployment

### Building the client app
```bash
cd client/
yarn install --frozen-lockfile
yarn run build
```


## License
EPL (Eclipse Public License)
Copyright ©  WormBase 2017

[1]: https://github.com/rkneufeld/conformity
[2]: https://clojure.org/community/downloads
[3]: https://clojure.org/about/spec
[4]: https://clojure.org/guides/getting_started
[5]: https://leiningen.org/
[6]: https://nodejs.org/en/
[7]: https://yarnpkg.com/en/docs/install
[8]: https://docs.docker.com/install/
[9]: https://docs.aws.amazon.com/cli/latest/userguide/installing.html
[10]: https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/eb-cli3-install.html
