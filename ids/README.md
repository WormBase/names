IDs
===

This sub-project provides a shared library used within datomic transactions and peer-queries.

Latest released version here:
[![Clojars Project](https://img.shields.io/clojars/v/wormbase/ids.svg)](https://clojars.org/wormbase/ids)

## Releases

```bash
rm -f pom.xml &&  make pom.xml
echo 'Ensure the groupId is "wormbase" and the artifact id is "ids" in pom.xml and update the version accordingly`.
echo `Check the current vesion with:`
echo "curl -s -H 'accept: application/json' https://clojars.org/api/artifacts/wormbase/ids"
```
make release
