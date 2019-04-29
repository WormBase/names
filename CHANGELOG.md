# Change-log

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
