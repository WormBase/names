# wormbase-names

A web app that facilitates the sharing of identifiers and names of a
subset of WormBase data types.

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

### Setup

```bash
cd /tmp
lein upgrade
```
Ensure you've installed the following software on your system:

#### Docker credentials

The Makefile target `ecr-login` command will, by default, store the
authentication token un-encrypted in the file: `~/.docker/config.json`.

There is a plugin that can be used to use a store to save these tokens encrypted,
but varies depending on operating system.

For linux, there's [docker-credential-pass][12] and [pass][13], which can be used together,
which uses a GPG2 key to encrypt tokens.


[clojure 1.9][4]

[leineningen 2.8.1+][5]

[nodejs][6]

[yarn][7]

[docker][8]

[awscli][9]

[awsebcli][10]

### Setup client app
Setup client app **either by [making a production build of the client app](#building-the-client-app) or running a client development server**, as show here:

- First, ensure `client/package.json` has proxy configured to point at the backend API.

- Then, run:
```bash
cd client/
yarn install
yarn run start
```

- Finally, ensure the authentication callback URL at Google Cloud Console is configured to match the client development server configuration.

Notes:
- `client/` is bootstrapped with [create-react-app][11], where you can find out more about its setup and capability
- **Port:**
    - To run the server on a different port:
```bash
PORT=[PORT] yarn run start
```
- **Dependencies:**
    - Most errors about missing dependencies can be resolved with `yarn install`.
    - It's safe to run `yarn install` when in doubt.
    - Refer to [yarn][7] documentation when modifying dependencies. And be sure to checking in changes in `yarn.lock` (auto-generated).
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

Run with `lein ring server [port]` or `lein ring server-headless [port]`.

To allow the UI webpackDevServer to proxy to the ring server, the ring server has to be run at the host and port configured in the `"proxy"` section in [client/package.json](client/package.json).


### Testing
Use built-in testing utilities as provided by leiningen.
Please run all tests before committing/submitting new pull requests.

NB:
Currently, these tests may occasionally fail with "could not generate after 100 attempts".
These are transient failures, to due to implementation of  clojure.spec generators in the test suite,
and can be ignored if they pass on subsequent runs.


## Releases

```bash
lein release
```

### Building the client application
```bash
make build-client-app
```

For a full list of tasks, type:

```bash
make help
```

## License
EPL (Eclipse Public License)
Copyright ©  WormBase 2018

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
[11]: https://github.com/facebook/create-react-app
[12]: https://github.com/docker/docker-credential-helpers/releases
[13]: https://github.com/docker/docker-credential-helpers/issues/102
