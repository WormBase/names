#!/bin/bash

artefact="docker/app.jar"
lein with-profile +prod ring uberjar
target="target/uberjar/app.jar"
if [ $? -eq 0 ]; then
    mv "${target}" "${artefact}"
    echo "${artefact}"
    exit 0
fi
exit 1
