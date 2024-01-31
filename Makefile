ECR_REPO_NAME := wormbase/names
EB_APP_ENV_FILE := app-env.config
PROJ_NAME ?= wormbase-names-dev
LOCAL_GOOGLE_REDIRECT_URI := "http://lvh.me:3000"
AWS_DEFAULT_REGION ?= us-east-1
ifeq ($(PROJ_NAME), wormbase-names)
	WB_DB_URI ?= "datomic:ddb://${AWS_DEFAULT_REGION}/WSNames/wormbase"
	GOOGLE_REDIRECT_URI ?= "https://names.wormbase.org"
	APP_PROFILE ?= "prod"
else ifeq ($(PROJ_NAME), wormbase-names-test)
	WB_DB_URI ?= "datomic:ddb://${AWS_DEFAULT_REGION}/WSNames-test-14/wormbase"
	GOOGLE_REDIRECT_URI ?= "https://test-names.wormbase.org"
	APP_PROFILE ?= "prod"
else
	WB_DB_URI ?= "datomic:ddb-local://localhost:8000/WBNames_local/wormbase"
#   Ensure GOOGLE_REDIRECT_URI is defined appropriately as an env variable or CLI argument
#   if intended for AWS deployment (default is set for local execution)
	GOOGLE_REDIRECT_URI ?= ${LOCAL_GOOGLE_REDIRECT_URI}
	APP_PROFILE ?= "dev"
endif

STORE_SECRETS_FILE = secrets.makedef

APP_JAR_PATH ?= build/app.jar
PORT := 3000
WB_ACC_NUM := 357210185381

ECR_URI := ${WB_ACC_NUM}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com
ECR_REPO_URI := ${ECR_URI}/${ECR_REPO_NAME}
ECR_IMAGE_URI = ${ECR_REPO_URI}:${VERSION_TAG}

# Define AWS (EB) CLI base commands as appropriate
AWS_CLI_BASE := aws
EB_CLI_BASE := eb
ifneq (${AWS_EB_PROFILE},)
	EB_CLI_BASE := ${EB_CLI_BASE} --profile ${AWS_EB_PROFILE}
ifeq (${AWS_PROFILE},)
	AWS_CLI_BASE := ${AWS_CLI_BASE} --profile ${AWS_EB_PROFILE}
endif
endif

ifneq (${AWS_PROFILE},)
	AWS_CLI_BASE := ${AWS_CLI_BASE} --profile ${AWS_PROFILE}
ifeq (${AWS_EB_PROFILE},)
	EB_CLI_BASE := ${EB_CLI_BASE} --profile ${AWS_PROFILE}
endif
endif

define target-help
	$1
		$2

endef

define print-help
        $(if $(need-help),$(info $(call target-help,$1,$2)))
endef

need-help := $(filter help,$(MAKECMDGOALS))

help: ; @echo $(if $(need-help),,\
	Type \'$(MAKE)$(dash-f) help\' to get help)

source-secrets:
ifneq ($(SECRETS_SRC), )
	@echo "Sourcing secrets file ${SECRETS_SRC}"
	$(eval SECRETS_CONTENT := $(shell cat "${SECRETS_SRC}"))
	$(foreach secret,${SECRETS_CONTENT},$(eval ${secret}))
endif

.PHONY: ENV.VERSION_TAG
ENV.VERSION_TAG: \
	$(call print-help,ENV.VERSION_TAG,\
	Extract the VERSION_TAG env variables for make targets from git tags if undefined.)
	$(eval REVISION_NAME_CMD = git describe --tags)
ifeq (${VERSION_TAG},)
# If REF_NAME is defined, convert provided ref in matching name
# (ref could be a tagname, a reference like "HEAD", branch-names...)
ifneq (${REF_NAME},)
	$(eval REVISION_NAME_CMD = ${REVISION_NAME_CMD} ${REF_NAME})
# Else, assume working-directory deployment
else
	$(eval REVISION_NAME_CMD = ${REVISION_NAME_CMD} --dirty=-DIRTY)
endif
	$(eval VERSION_TAG = $(shell ${REVISION_NAME_CMD} | sed 's/^wormbase-names-//'))
	$(call check_defined, VERSION_TAG, Ensure your working directory is a\
	                                   git clone of the repository)
	@echo "Retrieved VERSION_TAG '${VERSION_TAG}' from git tags."
else
	@echo "Using predefined VERSION_TAG '${VERSION_TAG}'."
endif

.PHONY: show-version
show-version: ENV.VERSION_TAG \
              $(call print-help,show-version,\
              Show the current application version.)
	@echo "${VERSION_TAG}"

build/:
	mkdir build/

build/datomic-pro-1.0.6165.zip:
	@echo "Downloading datomic bundle from S3."
	@${AWS_CLI_BASE} s3 cp s3://wormbase/datomic-pro/distro/datomic-pro-1.0.6165.zip build/

.PHONY: build-docker-image
build-docker-image: build/ ENV.VERSION_TAG ${STORE_SECRETS_FILE} build/datomic-pro-1.0.6165.zip \
                       $(call print-help,build,\
                       Build the docker image from the current git revision.)
	@docker build -t ${ECR_REPO_NAME}:${VERSION_TAG} \
		--secret id=make-secrets-file,src=${STORE_SECRETS_FILE} \
		.
	@rm ${STORE_SECRETS_FILE}

.PHONY: build-ui
build-ui: ENV.GOOGLE_OAUTH_CLIENT_ID \
          $(call print-help,build-ui,\
          Build JS and CSS file for release.)
	@ export REACT_APP_GOOGLE_OAUTH_CLIENT_ID=${GOOGLE_OAUTH_CLIENT_ID} && \
	  echo "Building UI using APP_PROFILE: '${APP_PROFILE}'" && \
	  ./scripts/build-ui.sh ${APP_PROFILE}

.PHONY: clean
clean: \
       $(call print-help,clean,\
       Remove the locally built UI and API artefacts.)
	@rm -f ${APP_JAR_PATH}
	@rm -rf client/build/*

${APP_JAR_PATH}: build/ \
                      $(call print-help,${APP_JAR_PATH},\
                      Build the jar file.)
	@./scripts/build-appjar.sh ${APP_JAR_PATH}

build-app-jar: ${APP_JAR_PATH} \
               $(call print-help,build-app-jar,\
               Build the jar file.)

.PHONY: build-local
build-local: clean build-ui build-app-jar \
              $(call print-help,build-local,\
              Build UI and app JAR (locally).)

.PHONY: docker-ecr-login
docker-ecr-login: \
                  $(call print-help,docker-ecr-login [AWS_PROFILE=<profile_name>],\
                  Login to ECR.)
	${AWS_CLI_BASE} ecr get-login-password | docker login -u AWS --password-stdin https://${ECR_URI}

.PHONY: docker-push-ecr
docker-push-ecr: docker-ecr-login \
                 $(call print-help,docker-push-ecr,\
                 Push the image tagged with the current git revision to ECR.)
	@docker push ${ECR_IMAGE_URI}

.PHONY: docker-tag
docker-tag: ENV.VERSION_TAG \
            $(call print-help,docker-tag,\
            Tag the image with current git revision and ':latest' alias.)
	@docker tag ${ECR_REPO_NAME}:${VERSION_TAG} ${ECR_IMAGE_URI}
	@docker tag ${ECR_REPO_NAME}:${VERSION_TAG} ${ECR_REPO_URI}

.PHONY: eb-def-app-env
eb-def-app-env: google-oauth2-secrets ENV.VERSION_TAG \
                $(call print-help,eb-def-app-env \
				[WB_DB_URI=<datomic-db-uri>] [GOOGLE_REDIRECT_URI=<google-redirect-uri>],\
                Define the ElasticBeanStalk app-environment config file.)
ifndef WB_DB_URI
	$(error WB_DB_URI not set! Define datomic-DB-URI as WB_DB_URI arg)
endif
ifndef GOOGLE_REDIRECT_URI
	$(error GOOGLE_REDIRECT_URI not set! Define the google redirect-uri to use for authentication as GOOGLE_REDIRECT_URI arg)
endif
	@cp ebextensions-templates/${EB_APP_ENV_FILE} .ebextensions/
	sed -i -r 's~(WB_DB_URI:\s+)".*"~\1"'"${WB_DB_URI}"'"~' .ebextensions/${EB_APP_ENV_FILE}
	sed -i -r 's~(API_GOOGLE_OAUTH_CLIENT_ID:\s+)".*"~\1"'"${GOOGLE_OAUTH_CLIENT_ID}"'"~' .ebextensions/${EB_APP_ENV_FILE}
	sed -i -r 's~(API_GOOGLE_OAUTH_CLIENT_SECRET:\s+)".*"~\1"'"${GOOGLE_OAUTH_CLIENT_SECRET}"'"~' .ebextensions/${EB_APP_ENV_FILE}
	sed -i -r 's~(GOOGLE_REDIRECT_URI:\s+)".*"~\1"'"${GOOGLE_REDIRECT_URI}"'"~' .ebextensions/${EB_APP_ENV_FILE}
	sed  -i -r 's~(WB_NAMES_RELEASE: ).+~\1'${VERSION_TAG}'~' .ebextensions/${EB_APP_ENV_FILE}

.PHONY: eb-create
eb-create: eb-def-app-env \
           $(call print-help,eb-create [AWS(_EB)?_PROFILE=<profile_name>] \
           [AWS_IAM_UNAME=<iam_username>] [PROJ_NAME=<eb-env-name>] [WB_DB_URI=<datomic-db-uri>] \
		   [GOOGLE_REDIRECT_URI=<google-redirect-uri>],\
           Create an ElasticBeanStalk environment using the Docker platform.)
	$(eval AWS_IAM_UNAME ?= $(shell test ${AWS_IAM_UNAME} && echo ${AWS_IAM_UNAME}\
	                             || ${AWS_CLI_BASE} iam get-user --query "User.UserName"))
	@test ${AWS_IAM_UNAME} || (\
		echo "Failed to retrieve IAM user-name. Define IAM username as AWS_IAM_UNAME arg." \
		&& exit 1 \
	)
	@${EB_CLI_BASE} create ${PROJ_NAME} \
	        --region=${AWS_DEFAULT_REGION} \
	        --tags="CreatedBy=${AWS_IAM_UNAME},Role=RestAPI" \
	        --cname="${PROJ_NAME}" \
	        -p docker \
			--elb-type application \
			--vpc.id vpc-8e0087e9 --vpc.ec2subnets subnet-1ce4c744,subnet-a33a2bd5 --vpc.elbsubnets subnet-1ce4c744,subnet-a33a2bd5 \
			--vpc.elbpublic --vpc.publicip

.PHONY: eb-deploy
eb-deploy: eb-def-app-env \
           $(call print-help,eb-deploy [PROJ_NAME=<eb-env-name>] \
           [AWS(_EB)?_PROFILE=<profile_name>] [WB_DB_URI=<datomic-db-uri>] \
		   [GOOGLE_REDIRECT_URI=<google-redirect-uri>],\
           Deploy the application using ElasticBeanstalk.)
	@${EB_CLI_BASE} deploy ${PROJ_NAME}

.PHONY: eb-env
eb-setenv: \
           $(call print-help,eb-env [AWS(_EB)_PROFILE=<profile_name>] [PROJ_NAME=<eb-env-name>] \
		   [WB_DB_URI=<datomic-uri>] [GOOGLE_REDIRECT_URI=<google-redirect-uri>],\
           Set enviroment variables for the ElasticBeanStalk environment.)
	@${EB_CLI_BASE} setenv \
		WB_DB_URI="${WB_DB_URI}" \
		GOOGLE_REDIRECT_URI="${GOOGLE_REDIRECT_URI}" \
		_JAVA_OPTIONS="-Xmx14g" \
		AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}" \
		AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}" \
		AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION}" \
		-e "${PROJ_NAME}"

.PHONY: eb-local
eb-local: docker-ecr-login \
          $(call print-help,eb-local [AWS(_EB)_PROFILE=<profile_name>] [PORT=<port>] \
		  [WB_DB_URI=<datomic-uri>] [GOOGLE_REDIRECT_URI=<google-redirect-uri>],\
          Runs the ElasticBeanStalk/docker build and run locally.)
	@${EB_CLI_BASE} local run --envvars PORT=${PORT},WB_DB_URI=${WB_DB_URI},GOOGLE_REDIRECT_URI=${GOOGLE_REDIRECT_URI}

#Note: the run-docker command can currently only be used with non-local WB_DB_URI value.
# Current setup fails to connect to local datomic DB (on host, outside of container)
# from within the application container.
# Making the host's "localhost" accessible within the container as "host.docker.internal"
# through the following options in the `docker run` command makes the DB URI accessible
# from within the container, but the transactor still fails to be accessible.
#     -e WB_DB_URI=datomic:ddb-local://host.docker.internal:8000/WBNames_local/wormbase
#     --add-host=host.docker.internal:host-gateway
.PHONY: run-docker
run-docker: ENV.VERSION_TAG clean-docker-run \
            $(call print-help,run [PORT=<port>] [PROJ_NAME=<docker-project-name>] \
	        [WB_DB_URI=<datomic-uri>] [GOOGLE_REDIRECT_URI=<google-redirect-uri>],\
            Run the application docker container (locally).)
	
	$(eval RUN_CMD = docker run \
		--name ${PROJ_NAME} \
		--publish-all=true \
		--publish ${PORT}:${PORT} \
		--detach \
		-e WB_DB_URI=${WB_DB_URI} \
		-e GOOGLE_REDIRECT_URI=${GOOGLE_REDIRECT_URI} \
		-e PORT=${PORT} \
		-e AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION})
ifneq (${AWS_ACCESS_KEY_ID},)
ifneq (${AWS_SECRET_ACCESS_KEY},)
	$(eval RUN_CMD = ${RUN_CMD} -e AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY})
	$(eval RUN_CMD = ${RUN_CMD} -e AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID})
ifneq (${AWS_SESSION_TOKEN},)
	$(eval RUN_CMD = ${RUN_CMD} -e AWS_SESSION_TOKEN=${AWS_SESSION_TOKEN})
endif
else
	@echo 'ENV var "AWS_ACCESS_KEY_ID" is defined but "AWS_SECRET_ACCESS_KEY" is not. Either define both or none.' >&2
	@exit 1
endif
else
ifneq (${AWS_PROFILE},)
	$(eval RUN_CMD = ${RUN_CMD} -e AWS_PROFILE=${AWS_PROFILE} -v ~/.aws:/root/.aws)
endif
endif

	${RUN_CMD} ${ECR_REPO_NAME}:${VERSION_TAG}

.PHONY: clean-docker-run
clean-docker-run: \
              $(call print-help,clean-docker-run [PROJ_NAME=<docker-project-name>],\
              Stop and remove the docker container (if running).)
# Stop if running container found
	@echo $(if $(shell docker ps -q --filter name=${PROJ_NAME}),$(shell docker stop ${PROJ_NAME})) > /dev/null
# Remove if stopped container found
	@echo $(if $(shell docker ps -a -q --filter name=${PROJ_NAME}),$(shell docker rm ${PROJ_NAME})) > /dev/null

.PHONY: deploy-ecr
deploy-ecr: docker-build docker-tag docker-push-ecr
            $(call print-help,deploy-ecr,\
            Deploy the application to the AWS container registry.)

.PHONY: vc-release
vc-release: ENV.VERSION_TAG \
            $(call print-help,vc-release LEVEL=<major|minor|patch>,\
            Perform the Version Control tasks to release the applicaton.)
	clj -A:release --without-sign ${LEVEL}
	@echo "Edit version of application in pom.xml to match wormbase-names-* version reported above (version number only)."


.PHONY: release
release: ENV.VERSION_TAG deploy-ecr \
         $(call print-help,release [AWS_PROFILE=<profile_name>] [REF_NAME=<tag-or-gitref>],\
         Release the applicaton.)

.PHONY: run-tests
run-tests: google-oauth2-secrets \
           $(call print-help,run-tests,\
           Run all tests.)
	@ export API_GOOGLE_OAUTH_CLIENT_ID=${GOOGLE_OAUTH_CLIENT_ID} && \
	  export API_GOOGLE_OAUTH_CLIENT_SECRET=${GOOGLE_OAUTH_CLIENT_SECRET} && \
	  export GOOGLE_REDIRECT_URI=${LOCAL_GOOGLE_REDIRECT_URI} && \
	  clojure -A:datomic-pro:logging:webassets:dev:test:run-tests

.PHONY: run-dev-webserver
run-dev-webserver: PORT := 4010
run-dev-webserver: google-oauth2-secrets \
                   $(call print-help,run-dev-webserver PORT=<port> WB_DB_URI=<datomic-uri> \
				   GOOGLE_REDIRECT_URI=<google-redirect-uri>,\
                   Run a local development webserver.)
	@ export WB_DB_URI=${WB_DB_URI} && export PORT=${PORT} && \
	 export GOOGLE_REDIRECT_URI=${GOOGLE_REDIRECT_URI} && \
	 export API_GOOGLE_OAUTH_CLIENT_ID=${GOOGLE_OAUTH_CLIENT_ID} && \
	 export API_GOOGLE_OAUTH_CLIENT_SECRET=${GOOGLE_OAUTH_CLIENT_SECRET} && \
	 clj -A:logging:datomic-pro:webassets:dev -m wormbase.names.service

.PHONY: run-dev-ui
run-dev-ui: google-oauth2-secrets\
		$(call print-help,run-dev-ui [REACT_APP_GOOGLE_OAUTH_CLIENT_ID=<google-client-id>] \
		Run a local development UI.)
	@export REACT_APP_GOOGLE_OAUTH_CLIENT_ID=${GOOGLE_OAUTH_CLIENT_ID} && \
	 cd client/ && \
	 . $(HOME)/.nvm/nvm.sh && nvm use && \
	 npm install && \
	 npm run start

.PHONY: ENV.GOOGLE_OAUTH_CLIENT_ID
ENV.GOOGLE_OAUTH_CLIENT_ID: source-secrets \
	$(call print-help,ENV.GOOGLE_OAUTH_CLIENT_ID,\
	Retrieve the GOOGLE_OAUTH_CLIENT_ID env variable for make targets from aws ssm if undefined.)
	$(eval ACTION_MSG := $(if ${GOOGLE_OAUTH_CLIENT_ID},"Using predefined GOOGLE_OAUTH_CLIENT_ID.","Retrieving GOOGLE_OAUTH_CLIENT_ID from AWS SSM (APP_PROFILE '${APP_PROFILE}')."))
	@echo ${ACTION_MSG}
	$(if ${GOOGLE_OAUTH_CLIENT_ID},,$(eval GOOGLE_OAUTH_CLIENT_ID := $(shell ${AWS_CLI_BASE} ssm get-parameter --name "/name-service/${APP_PROFILE}/google-oauth2-app-config/client-id" --query "Parameter.Value" --output text --with-decryption)))
	$(call check_defined, GOOGLE_OAUTH_CLIENT_ID, Check the defined APP_PROFILE value\
	 and ensure the AWS_PROFILE variable is appropriately defined)

.PHONY: ENV.GOOGLE_OAUTH_CLIENT_SECRET
ENV.GOOGLE_OAUTH_CLIENT_SECRET: source-secrets \
	$(call print-help,ENV.GOOGLE_OAUTH_CLIENT_SECRET,\
	Retrieve the GOOGLE_OAUTH_CLIENT_SECRET env variable for make targets from aws ssm if undefined.)
	$(eval ACTION_MSG := $(if ${GOOGLE_OAUTH_CLIENT_SECRET},"Using predefined GOOGLE_OAUTH_CLIENT_SECRET.","Retrieving GOOGLE_OAUTH_CLIENT_SECRET from AWS SSM (APP_PROFILE '${APP_PROFILE}')."))
	@echo ${ACTION_MSG}
	$(if ${GOOGLE_OAUTH_CLIENT_SECRET},,$(eval GOOGLE_OAUTH_CLIENT_SECRET := $(shell ${AWS_CLI_BASE} ssm get-parameter --name "/name-service/${APP_PROFILE}/google-oauth2-app-config/client-secret" --query "Parameter.Value" --output text --with-decryption)))
	$(call check_defined, GOOGLE_OAUTH_CLIENT_SECRET, Check the defined APP_PROFILE value\
	 and ensure the AWS_PROFILE variable is appropriately defined)

.PHONY: google-oauth2-secrets
google-oauth2-secrets: ENV.GOOGLE_OAUTH_CLIENT_ID ENV.GOOGLE_OAUTH_CLIENT_SECRET \
                       $(call print-help,google-oauth2-secrets,\
                       Store the Google oauth2 client details as env variables.)

${STORE_SECRETS_FILE}: google-oauth2-secrets
	@install -m 600 /dev/null ${STORE_SECRETS_FILE}
	@echo "GOOGLE_OAUTH_CLIENT_ID:=${GOOGLE_OAUTH_CLIENT_ID}" >> ${STORE_SECRETS_FILE}
	@echo "GOOGLE_OAUTH_CLIENT_SECRET:=${GOOGLE_OAUTH_CLIENT_SECRET}" >> ${STORE_SECRETS_FILE}

# Check that given variables are set and all have non-empty values,
# die with an error otherwise.
#
# Params:
#   1. Variable name(s) to test.
#   2. (optional) Error message to print.
check_defined = \
    $(strip $(foreach 1,$1, \
        $(call __check_defined,$1,$(strip $(value 2)))))
__check_defined = \
    $(if $(value $1),, \
      $(error Failed to define $1. $(if $2, $2)))
