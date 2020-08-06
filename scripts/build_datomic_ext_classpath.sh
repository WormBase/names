#!/bin/bash

# build the classpath for the datomic transactor using tools.deps
if [ -z "$CONSOLE_DEVICE" ]; then
    CONSOLE_DEVICE=/dev/console
fi

print_log () {
    local msg=$1;
    #Print logs to STDERR and to AWS console (System Log)
    >&2 echo $1
    echo $1 > $CONSOLE_DEVICE
}

JQ="/usr/local/bin/jq"
CLOJURE="/usr/local/bin/clojure"
ARTIFACT_INFO=$(curl -s -H 'accept: application/json' https://clojars.org/api/artifacts/wormbase/ids)
ARTIFACT_NAME=$(echo $ARTIFACT_INFO | $JQ '"\(.group_name)/\(.jar_name)"' | tr -d '"')
ARTIFACT_VERSION=$(echo $ARTIFACT_INFO | $JQ '"\(.recent_versions[0].version)"')

print_log "ARTIFACT NAME: $ARTIFACT_NAME"
print_log "ARTIFACT VERSION: $ARTIFACT_VERSION"

TRANSACTOR_DEPS="{:deps {$ARTIFACT_NAME {:mvn/version $ARTIFACT_VERSION}}}"

DEPS=$($CLOJURE -Spath -Sdeps "$TRANSACTOR_DEPS" | sed 's|:|\n|g' | grep "wormbase/ids")
print_log "DEPS:"
print_log "$DEPS"

echo $DEPS
