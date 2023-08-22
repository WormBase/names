ECR_REPO_NAME := wormbase/names
EB_APP_ENV_FILE := app-env.config
PROJ_NAME ?= "wormbase-names"
LOCAL_GOOGLE_REDIRECT_URI = "http://lvh.me:3000"
ifeq ($(PROJ_NAME), wormbase-names)
	WB_DB_URI ?= "datomic:ddb://us-east-1/WSNames/wormbase"
	GOOGLE_REDIRECT_URI ?= "https://names.wormbase.org"
else ifeq ($(PROJ_NAME), wormbase-names-test)
	WB_DB_URI ?= "datomic:ddb://us-east-1/WSNames-test-14/wormbase"
	GOOGLE_REDIRECT_URI ?= https://test-names.wormbase.org/
else
	WB_DB_URI ?= "datomic:ddb://us-east-1/WSNames-test-14/wormbase"
#   Ensure GOOGLE_REDIRECT_URI is defined as appropriate as an env variable or CLI argument
endif
DEPLOY_JAR := app.jar
PORT := 3000
WB_ACC_NUM := 357210185381

REVISION_NAME_CMD := git describe --tags
# If REF_NAME is defined, convert provided ref in matching name
# (ref could be a tagname, a reference like "HEAD", branch-names...)
ifneq (${REF_NAME},)
	REVISION_NAME := $(shell ${REVISION_NAME_CMD} ${REF_NAME})
# Else, assume working-directory deployment
else
	REVISION_NAME := $(shell ${REVISION_NAME_CMD} --dirty=-DIRTY)
endif

VERSION_TAG ?= $(shell echo "${REVISION_NAME}" | sed 's/^wormbase-names-//')

ECR_URI := ${WB_ACC_NUM}.dkr.ecr.us-east-1.amazonaws.com
ECR_REPO_URI := ${ECR_URI}/${ECR_REPO_NAME}
ECR_IMAGE_URI := ${ECR_REPO_URI}:${VERSION_TAG}
# Set AWS (EB) profile env vars if undefined
ifneq (${AWS_PROFILE},)
	AWS_EB_PROFILE ?= ${AWS_PROFILE}
endif
ifneq (${AWS_EB_PROFILE},)
	AWS_PROFILE ?= ${AWS_EB_PROFILE}

	export AWS_EB_PROFILE
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

.PHONY: show-version
show-version: \
              $(call print-help,show-version,\
              Show the current application version.)
	@echo "${VERSION_TAG}"

.PHONY: build
build: clean ui-build docker/${DEPLOY_JAR} \
       $(call print-help,build,\
       Build the docker images from using the current git revision.)
	@docker build -t ${ECR_REPO_NAME}:${VERSION_TAG} \
		--build-arg uberjar_path=${DEPLOY_JAR} \
		./docker/

.PHONY: ui-build
ui-build: \
          $(call print-help,ui-build,\
          Build JS and CSS file for release.)
	@./scripts/build-ui.sh

.PHONY: clean
clean: \
       $(call print-help,clean,\
       Remove the locally built JAR file.)
	@rm -f ./docker/${DEPLOY_JAR}

docker/${DEPLOY_JAR}: \
                      $(call print-help,docker/${DEPLOY_JAR},\
                      Build the jar file.)
	@./scripts/build-appjar.sh

.PHONY: docker-build
docker-build: clean build \
              $(call print-help,docker-build,\
              Create docker container.)

.PHONY: docker-ecr-login
docker-ecr-login: \
                  $(call print-help,docker-ecr-login [AWS_PROFILE=<profile_name>],\
                  Login to ECR.)
	aws --profile ${AWS_PROFILE} ecr get-login-password | docker login -u AWS --password-stdin https://${ECR_URI}

.PHONY: docker-push-ecr
docker-push-ecr: docker-ecr-login \
                 $(call print-help,docker-push-ecr,\
                 Push the image tagged with the current git revision to ECR.)
	@docker push ${ECR_IMAGE_URI}

.PHONY: docker-tag
docker-tag: \
            $(call print-help,docker-tag,\
            Tag the image with current git revision and ':latest' alias.)
	@docker tag ${ECR_REPO_NAME}:${VERSION_TAG} ${ECR_IMAGE_URI}
	@docker tag ${ECR_REPO_NAME}:${VERSION_TAG} ${ECR_REPO_URI}

.PHONY: eb-def-app-env
eb-def-app-env: \
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
	sed -i -r 's~(GOOGLE_REDIRECT_URI:\s+)".*"~\1"'"${GOOGLE_REDIRECT_URI}"'"~' .ebextensions/${EB_APP_ENV_FILE}
	sed  -i -r 's~(WB_NAMES_RELEASE: ).+~\1'${VERSION_TAG}'~' .ebextensions/${EB_APP_ENV_FILE}

.PHONY: eb-create
eb-create: eb-def-app-env \
           $(call print-help,eb-create [AWS(_EB)?_PROFILE=<profile_name>] \
           [AWS_IAM_UNAME=<iam_username>] [PROJ_NAME=<eb-env-name>] [WB_DB_URI=<datomic-db-uri>] \
		   [GOOGLE_REDIRECT_URI=<google-redirect-uri>],\
           Create an ElasticBeanStalk environment using the Docker platform.)
	$(eval AWS_IAM_UNAME ?= $(shell test ${AWS_IAM_UNAME} && echo ${AWS_IAM_UNAME}\
	                             || aws --profile ${AWS_PROFILE} iam get-user --query "User.UserName"))
	@test ${AWS_IAM_UNAME} || (\
		echo "Failed to retrieve IAM user-name. Define IAM username as AWS_IAM_UNAME arg." \
		&& exit 1 \
	)
	@eb create ${PROJ_NAME} \
	        --region=us-east-1 \
	        --tags="CreatedBy=${AWS_IAM_UNAME},Role=RestAPI" \
	        --cname="${PROJ_NAME}" \
	        -p docker \
			--elb-type application \
			--vpc.id vpc-8e0087e9 --vpc.ec2subnets subnet-1ce4c744,subnet-a33a2bd5 --vpc.elbsubnets subnet-1ce4c744,subnet-a33a2bd5 \
			--vpc.elbpublic --vpc.publicip

.PHONY: eb-deploy
eb-deploy: eb-def-app-env \
           $(call print-help,eb-deploy [PROJ_NAME=<eb-env-name>] \
           [AWS_EB_PROFILE=<profile_name>] [WB_DB_URI=<datomic-db-uri>] \
		   [GOOGLE_REDIRECT_URI=<google-redirect-uri>],\
           Deploy the application using ElasticBeanstalk.)
	@eb deploy ${PROJ_NAME}

.PHONY: eb-env
eb-setenv: \
           $(call print-help,eb-env [AWS_EB_PROFILE=<profile_name>] [PROJ_NAME=<eb-env-name>] \
		   [WB_DB_URI=<datomic-uri>] [GOOGLE_REDIRECT_URI=<google-redirect-uri>],\
           Set enviroment variables for the ElasticBeanStalk environment.)
	@eb setenv \
		WB_DB_URI="${WB_DB_URI}" \
		GOOGLE_REDIRECT_URI="${GOOGLE_REDIRECT_URI}" \
		_JAVA_OPTIONS="-Xmx14g" \
		AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}" \
		AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}" \
		-e "${PROJ_NAME}"

.PHONY: eb-local
eb-local: docker-ecr-login \
          $(call print-help,eb-local [AWS_EB_PROFILE=<profile_name>] [PORT=<port>] \
		  [WB_DB_URI=<datomic-uri>] [GOOGLE_REDIRECT_URI=<google-redirect-uri>],\
          Runs the ElasticBeanStalk/docker build and run locally.)
	@eb local run --envvars PORT=${PORT},WB_DB_URI=${WB_DB_URI},GOOGLE_REDIRECT_URI=${GOOGLE_REDIRECT_URI}

.PHONY: run
run: \
     $(call print-help,run [PORT=<port>] [PROJ_NAME=<docker-project-name>] \
	 [WB_DB_URI=<datomic-uri>] [GOOGLE_REDIRECT_URI=<google-redirect-uri>],\
     Run the application in docker (locally).)
	@docker run \
		--name ${PROJ_NAME} \
		--publish-all=true \
		--publish ${PORT}:${PORT} \
		--detach \
		-e AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY} \
		-e AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID} \
		-e WB_DB_URI=${WB_DB_URI} \
		-e GOOGLE_REDIRECT_URI=${GOOGLE_REDIRECT_URI} \
		-e PORT=${PORT} \
		${ECR_REPO_NAME}:${VERSION_TAG}

.PHONY: docker-clean
docker-clean: \
              $(call print-help,docker-clean [PROJ_NAME=<docker-project-name>],\
              Stop and remove the docker container (if running).)
	@docker stop ${PROJ_NAME}
	@docker rm ${PROJ_NAME}

.PHONY: deploy-ecr
deploy-ecr: docker-build docker-tag docker-push-ecr
            $(call print-help,deploy-ecr,\
            Deploy the application to the AWS container registry.)

.PHONY: vc-release
vc-release: export VERSION_TAG := ${VERSION_TAG}
vc-release: $(call print-help,vc-release LEVEL=<major|minor|patch>,\
            Perform the Version Control tasks to release the applicaton.)
	clj -A:release --without-sign ${LEVEL}
	@echo "Edit version of application in pom.xml to match wormbase-names-* version reported above (version number only)."


.PHONY: release
release: export VERSION_TAG := ${VERSION_TAG}
release: deploy-ecr \
         $(call print-help,release [AWS_PROFILE=<profile_name>] [REF_NAME=<tag-or-gitref>],\
         Release the applicaton.)

.PHONY: run-tests
run-tests: \
           $(call print-help,run-tests,\
           Run all tests.)
	@GOOGLE_REDIRECT_URI=${LOCAL_GOOGLE_REDIRECT_URI} clj -A:datomic-pro:webassets:dev:test:run-tests

.PHONY: run-dev-server
run-dev-webserver: \
                   $(call print-help,run-dev-webserver PORT=<port> WB_DB_URI=<datomic-uri> \
				   GOOGLE_REDIRECT_URI=<google-redirect-uri>,\
                   Run a local development webserver.)
	@clj -A:logging:datomic-pro:webassets:dev -m wormbase.names.service
