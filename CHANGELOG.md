# Change-log

## [0.9.7]
 - Improved generated swagger docs.

## [0.9.6]
 - Fixed batch update bugs.

## [0.9.5]
 - Removed payload from consideration when calcualting batch-size.
 - Minor swagger docs fixes.
 - Added doc-strings.

## [0.9.4]
 - Now use npm instead of yarn for managing the client application.
 - Fixed bugs in retract names endpoints (batch API)
 - Fixed bugs related to whether a generic entity requires a name or not.

## [0.9.0]
 - Batch API data format change to change status endpoint.

## [0.6.16]
 - Added temporary hack to allow nil values in provenance due to bad data.

## [0.6.15]
 - Unqualified names for all provenance values (how and what where
   still rendered as clojure keywords in response bodies).

## [0.6.14]
 - Temporary fix for missing person provenance (importer defect).

## [0.6.13]
 - Fixed aws version bumping in Dockerrun.aws.json.

## [0.6.12]
 - Feature: Generic Entity API
 - Updated deployment process (removed use of lein in favour of clojure CLI tools).

## [0.5.3]
 - Species listing API
 - Fixed species-specifix regular expression pattern for gene names.
 - Match up gene identifiers with names in batch creation endpoints.

## [0.5.2]
 - UI fix for recent activities.
 - Added --profile flag for command for running awsebcli commands in Makefile.

## [0.5.1]
 - Redeploy of 0.5.0 to fix broken jar within container.

## [0.5.0]
 - Fix: rendering of static assets when run from jar.

## [0.4.22]
 - Require authentication for all endpoints.
 - UI improvement: display message when no recent activities.
 - Added ability to export current entity state (separate executable jar).
 - Added endpoint to provide stats (entity counts).

## [0.4.21]
 - Fixed species update API.

## [0.4.20]
 - Fixed recent activities E-tag/caching bug.
 - Fixed bugs with error handling of batch operations.
 - Applied patch to fix initial data spec of per species cgc-name regular expressions.

## [0.4.19]
 - UI for recent activities.
 - UI bufix for blank sequence name.

## [0.4.18]
 - Configured AWS Elasticbeanstalk app to use m4.xlarge EC2 instance type.

## [0.4.17]
 - Configured AWS Elasticbeanstalk HTTP termination-at-instance.

## [0.4.16]
 - Fixed incorrect conversion logic for gene history.
 - Speed up gene history calculation by an order of magnitude.
 - "Recent" API used to display recent activity and query what's changed over a given time period.
 - Various improvements to UI/UX.
 - Refactoring of UI code to make it easier to add new types.
 - Improvements to automatically generated documentation.

## [0.4.15]
 - Fixed syntax error.

## [0.4.14]
 - Fixed yaml config file format.

## [0.4.13]

## [0.4.12]
 - Bumped version of wormbase/ids

## [0.4.11]
 - Moved cognitect.transcriptor from dev to main dependency list to fix AWS startup error.

## [0.4.10]
 - Fixed resolving of references and values in gene history changes.

## [0.4.9]
 - Implemented variations API.
 - Implemented Species API.
 - Implemented recent API.
 - Fixed issues with parsing of authentication tokens causing them to appear as expired.
 - Fixed several issues with batch API.
 - Implemented importer for variations.
 - Changed importer tool to work for genes and variations.
 - Fixed gene importer to ignore duplicate names on dead genes.

## [0.4.8]
 - Fixed status assignment in batch api.

## [0.4.7]
 - Schema migration for :batch/id
 - Batch API: new entities always get a "live" status.

## [0.4.6]
 - Corrected batch/id spec.
 - Corrected swagger doc summaries.

## [0.4.5]
 - Corrected provenance spec.

## [0.4.4]
 - Included wormbase.ids uberjar building.
 - Added leiningen release tasks.

## [0.4.3]
 - Added Batch API (new, update, resurrect, kill end-points)
 - Multiple bug fixes and features.
 - Multiple UI/UX changes.

## [0.1-alpha] - (un-released)
 - Creation of new named genes.
