#!/bin/bash

ARTEFACT=$1
TMP_JAR=$(mktemp --dry-run target/wb-names-app-XXXXXXXXXX.jar)

rm -rf target
mkdir -p target
clj -Spom
clj -A:logging:prod:datomic-pro:webassets:depstar -m hf.depstar.uberjar "${TMP_JAR}"
if [ $? -eq 0 ]; then
    mv "${TMP_JAR}" "${ARTEFACT}"
    exit 0
else
    echo >&2 "Failed to build jar '${TMP_JAR}'"
    exit 1
fi

