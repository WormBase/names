# wormbase-names

This library will provide:
 - A rest API upon which a web application can be written to replace
   the existing "name server"
 - schema and database related functions
   The schema and related database functions are intended to evolve to eventually become the "central" database within the WormBase architecture.
   The final implementation may not necessarily reside in/be this
   library (or the clojure namespace defined herein.

## The existing WormBase "name server" - background
The current wormbase name service is a web application backed by a
MySQL database. It exists to facilitate pan-institution sharing of
WormBase identifiers for `Gene`, `Feature` and `Variation` entities.

### Run the application locally

`lein ring server-headless`

### Packaging and running as standalone jar

```bash
lein do clean, ring uberjar
java -jar target/server.jar
```

### Testing
Use built-in testing utilities as provided by leiningen.
Please run before committing/submitting new pull requests.

```bash
lein test
```

```bash
lein do clean, test-refresh
```

## License
EPL (Eclipse Public License)
Copyright ©  WormBase 2017
