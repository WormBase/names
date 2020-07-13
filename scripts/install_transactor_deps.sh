#!/bin/bash

if [ -z $CONSOLE_DEVICE ]; then
    CONSOLE_DEVICE=/dev/console
fi

CLOJURE="/usr/local/bin/clojure"
JQ="/usr/local/bin/jq"

aws_console () {
    local msg=$1;
    echo $1 > $CONSOLE_DEVICE
}

TOOLS_DEPS_VERSION="1.10.1.502"

install_tools_deps () {
    aws_console "Installing tools deps...."
    cd /tmp
    local tools_deps_installer_filename="linux-install-${TOOLS_DEPS_VERSION}.sh"
    curl --silent -O "https://download.clojure.org/install/${tools_deps_installer_filename}"
    chmod +x "${tools_deps_installer_filename}"
    ./$tools_deps_installer_filename 2> /dev/null > /dev/null
    aws_console "installed tools.deps"
}

# install tools.deps (clojure)
[ ! -e $CLOJURE ] && install_tools_deps;

# install jq if needed
if [ ! -e $JQ ]; then
    aws_console "Downloading and installing jq"
    wget -O $JQ --quiet https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64
    chmod +x $JQ
fi
