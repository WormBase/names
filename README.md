# wormbase-names

This library will provide:
 - A rest API upon which a web application can be written to replace
   the existing "name server"
 - schema and database related functions
   The schema and related database functions are intended to evolve to eventually become the "central" database within the WormBase architecture.
   The final implementation may not necessarily reside in/be this
   library (or the clojure namespace defined herein.


# Datomic schema design/approach
The current implementation uses schema defined in Clojure (using
[datomic.schema][1]) which makes it transparent the set of attributes
available from attributes of type `:db.type/ref`.

Provence about changes to entities (who, when, where and why) are
modelled as attributes on the "transaction entity".

We may experiment with modelling some of attributes in the same way as
the automatically converted ACeDB database; such that we can compare
and contrast the benefits and drawbacks of using each.

[Conformity][2] is used to idemoptently transaction the database schema at runtime.

The latest alpha version of [clojure][3] is used to facilitate the use
of [clojure.spec][4] to provide validation and test-data-generation for
testing.

## The existing WormBase "name server" - background
The current wormbase name service is a web application backed by a
MySQL database. It exists to facilitate pan-institution sharing of
WormBase identifiers and names for `Gene`, `Feature` and `Variation`
entities (ACeDB does not support concurrent write-access).


### Run the application locally
For now, using a local dev or memory datomic db for testing.
Run with:

```bash
export WB_DB_URI="datomic:mem://names-test"
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

## License
EPL (Eclipse Public License)
Copyright ©  WormBase 2017

[1]: https://github.com/gfZeng/datomic.schema
[2]: https://github.com/rkneufeld/conformity
[3]: https://clojure.org/community/downloads
[4]: https://clojure.org/about/spec
