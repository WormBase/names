IDs
===

This sub-project provides a shared library used within datomic transactions and peer-queries.

Latest released version here:
[![Clojars Project](https://img.shields.io/clojars/v/wormbase/ids.svg)](https://clojars.org/wormbase/ids)

All commands below assume the ids directory as the starting-point working directory.

## Build
```bash
make clean
make target/wbids.jar
```

## Test
Ensure to run all tests and check they pass before deploying a new release, submitting new pull requests
and preferable before committing to the IDs library code.
```bash
cd ../
make run-tests
cd -
```

## Release & deployment
The IDs library deployment process consists of three main steps:
1. Build and deploy the library to clojars
2. Update the appropriate Datomic transactors to use this new library
3. Update the main name-service to use the new version of the `wormbase/ids` dependency
    and release and deploy a new version of the application (if needed).

### Build and deploy to clojars

1. Prepare pom.xml file defining the release version and dependencies
```bash
# Generate/update the pom.xml file (not version-controlled)
rm -f pom.xml
clj -Spom

#Check the current clojars version
curl -s -H 'accept: application/json' https://clojars.org/api/artifacts/wormbase/ids | jq .latest_version

# Update the pom.xml to
#   * have a new unique version as <version> tag value (bump x, y or z release nr from current version)
#   * have "wormbase" (unquoted) as <groupId> tag value
#   * have "ids" (unquoted) as <artifactId> tag value
$EDITOR pom.xml
```

2. Build the library jar as [instructed above](#build).

3. Deploy the library jar to clojars.  
    This will require the file `~/.m2/settings.xml` to be defined as described [here](https://github.com/clojars/clojars-web/wiki/pushing#maven) (`settings.xml` part).
    Clojars username can be obtained by registering at [clojars.org](https://clojars.org)
    and a deploy-token can be generated after that by visiting [this page](https://clojars.org/tokens).
    Ensure you have been added to the wormbase group to allow uploading a new version (ask a colleague).
```bash
# Release to clojars
make release
```

### Update Datomic transactors
For instructions on how to update the datomic transactors
so they would use a new release of the IDs library,
see the [wormbase-architecture/transactor README](https://github.com/WormBase/wormbase-architecture/tree/develop/transactor#rolling-updates-change-set).

### Update the main name-service application dependency
Update the main name-service to use the new version of the `wormbase/ids` dependency by:
 1. Updating it's [`deps.edn`](../deps.edn) file to point to the latest `wormbase/ids` release.
 2. Release and deploy a new version of the main application (if needed),
    as describe in the main application's [README](../README.md#deploying-application).
