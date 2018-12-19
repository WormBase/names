#!/bin/sh

which jq > /dev/null
if [ $? -ne 0 ]; then
    echo "This script requires the commmand line utility 'jq' - please install it!"
    exit 1
fi

# build deps
ARTIFACT_INFO=$(curl -s -H "accept: application/json" https://clojars.org/api/artifacts/wormbase/ids)
ARTIFACT_NAME=$(echo $ARTIFACT_INFO | jq '"\(.group_name)/\(.jar_name)"' | tr -d '"')
ARTIFACT_VERSION=$(echo $ARTIFACT_INFO | jq '"\(.recent_versions[-1].version)"')
DEPS_EDN="{:deps {${ARTIFACT_NAME} {:mvn/version ${ARTIFACT_VERSION}}}}"
echo $DEPS_EDN
