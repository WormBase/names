#!/bin/bash
APP_PROFILE=$1

cd client/

if [ "${APP_PROFILE}" == "dev" ]; then
    echo "Activating nvm"
    export NVM_DIR="$HOME/.nvm"

    [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"  # This loads nvm
    [ -s "$NVM_DIR/bash_completion" ] && \. "$NVM_DIR/bash_completion"  # This loads nvm bash_completion
    nvm use
fi

npm ci
npm run build
