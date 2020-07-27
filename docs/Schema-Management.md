The Datomic schema resides primarily in the database.

When creating a database from scratch, the schema is read from the `/resources/schema` folder.
Below is a synopsis of each file and its purpose.

# Files

* [`definitions.edn`](../resources/schema/definitions.edn)  
  Main schema attributes, relations.

* [`seed-data.edn`](../resources/schema/seed-data.edn)  
  Collection of constants, enumerations that could be considered "Data" or "catalogue" (e.g biotype enumerations)

* [`wbpeople.edn`](../resources/schema/wbpeople.edn)  
  Data for populating users.

# Updates
Currently, a simple mechanism is used to apply updates.
clojure expressions are read from `.repl` files in `resources/schema/updates` and applied at the repl using [coginitect.transcriptor][1].

Enter a repl:
```bash
clj -A:datomic-pro
```
Then apply all updates (safe, as changes are idempotent):
```clojure
(require '[cognitect.transcriptor :as xr])
(doseq [rf (xr/repl-files "resources/schema/updates")] (xr/run rf))
```

[1]:https://github.com/cognitect-labs/transcriptor/
