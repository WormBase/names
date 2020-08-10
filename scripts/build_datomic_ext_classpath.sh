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

print_log "ARTIFACT NAME: $ARTIFACT_NAME"

if [ -z "$ARTIFACT_VERSION" ]; then
    ARTIFACT_VERSION=$(echo $ARTIFACT_INFO | $JQ '"\(.latest_version)"' | tr -d '"')
    print_log "ARTIFACT VERSION (latest): $ARTIFACT_VERSION"
else
    print_log "ARTIFACT VERSION (env): $ARTIFACT_VERSION"
fi

TRANSACTOR_DEPS="{:deps {$ARTIFACT_NAME {:mvn/version \"$ARTIFACT_VERSION\"}}}"

DEPS=$($CLOJURE -Spath -Sdeps "$TRANSACTOR_DEPS" | sed 's|:|\n|g' | grep "wormbase/ids")
print_log "DEPS: $DEPS"

echo $DEPS
