#!/bin/bash

if [ -z $CONSOLE_DEVICE ]; then
    CONSOLE_DEVICE=/dev/console
fi

aws_console () {
    local msg=$1;
    echo $1 > $CONSOLE_DEVICE
}

TOOLS_DEPS_VERSION="1.9.0.397"

# install tools.deps (clojure)
clojure=$(which clojure)
if [ $? -ne 0 ]; then
    if [ `$(cat "$clojure" | awk '/Version/{print $NF}'` = $TOOLS_DEPS_VERSION ]; then
	aws_console "Already have the latest version of tools deps, not installing."
    else
	aws_console "Installing tools deps...."
	cd /tmp
	tools_deps_installer_filename="linux-install-${TOOLS_DEPS_VERSION}.sh"
	curl -O "https://download.clojure.org/install/${tools_deps_installer_filename}"
	chmod +x "${tools_deps_installer_filename}"
	./$tools_deps_installer_filename
    fi
fi

# install jq if needed
which jq > /dev/null
if [ $? -ne 0 ]; then
    echo "Downloading and installing jq"
    wget -O /usr/local/bin/jq --quiet https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64
    chmod +x /usr/local/bin/jq
fi


# build the classpath for the datomic transactor using tools.deps
ARTIFACT_INFO=$(curl -s -H 'accept: application/json' https://clojars.org/api/artifacts/wormbase/ids)
ARTIFACT_NAME=$(echo $ARTIFACT_INFO | jq '"\(.group_name)/\(.jar_name)"' | tr -d '"')
ARTIFACT_VERSION=$(echo $ARTIFACT_INFO | jq '"\(.recent_versions[-1].version)"')

TRANSACTOR_DEPS="{:deps {$ARTIFACT_NAME {:mvn/version $ARTIFACT_VERSION}}}"
clojure -Spath -Sdeps "$TRANSACTOR_DEPS"
