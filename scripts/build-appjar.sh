#!/bin/bash

ARTEFACT="docker/app.jar"
LATEST_TAG=$(git describe --abbrev=0)
RELEASE_NAME="wormbase-names-${LATEST_TAG}"
DEPLOY_JAR="target/${RELEASE_NAME}.jar"

rm -rf target
mkdir -p target
clj -Spom
clj -A:datomic-pro:webassets:depstar -m hf.depstar.uberjar "${DEPLOY_JAR}"
if [ $? -eq 0 ]; then
    mv "${DEPLOY_JAR}" "${ARTEFACT}"
    echo "${ARTEFACT}"
    exit 0
else
    exit 1
fi

