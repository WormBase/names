#!/bin/bash -l

cd client/
nvm exec npm ci
nvm exec npm run build
