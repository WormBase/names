ECR_REPO_NAME := wormbase/names
EB_APP_ENV_FILE := app-env.config
PROJ_NAME ?= "wormbase-names"
ifeq ($(PROJ_NAME), "wormbase-names")
	WB_DB_URI ?= "datomic:ddb://us-east-1/WSNames/wormbase"
else
	WB_DB_URI ?= "datomic:ddb://us-east-1/WSNames-test-14/wormbase"
endif
DEPLOY_JAR := app.jar
PORT := 3000
WB_ACC_NUM := 357210185381
VERSION ?= $(shell clj -A:spit-version -v | jq .version)
ARTIFACT_NAME ?= $(shell git describe --tags --abbrev=0)
FQ_TAG := ${WB_ACC_NUM}.dkr.ecr.us-east-1.amazonaws.com/${ECR_REPO_NAME}:${VERSION}
# Set AWS (EB) profile env vars if undefined
AWS_EB_PROFILE ?= ${AWS_PROFILE}
AWS_PROFILE ?= ${AWS_EB_PROFILE}

define print-help
        $(if $(need-help),$(warning $1 -- $2))
endef

need-help := $(filter help,$(MAKECMDGOALS))

help: ; @echo $(if $(need-help),,\
	Type \'$(MAKE)$(dash-f) help\' to get help)

.PHONY: show-version
show-version: $(call print-help,show-version,"Show the current application version.")
	@echo "${VERSION}"

.PHONY: build
build: clean \
       ui-build \
       docker/${DEPLOY_JAR} \
       $(call print-help,build,\
	"Build the docker images from using the current git revision.")
	@docker build -t ${ECR_REPO_NAME}:${VERSION} \
		--build-arg uberjar_path=${DEPLOY_JAR} \
		--rm ./docker/

.PHONY: ui-build
ui-build: $(call print-help,ui-build,"Build JS and CSS file for release")
	@./scripts/build-ui.sh

.PHONY: clean
clean: $(call print-help,clean,"Remove the locally built JAR file.")
	@rm -f ./docker/${DEPLOY_JAR}

docker/${DEPLOY_JAR}: $(call print-help,docker/${DEPLOY_JAR},\
		       "Build the jar file")
	@./scripts/build-appjar.sh

.PHONY: docker-build
docker-build: clean build \
              $(call print-help,docker-build, "Create docker container")

.PHONY: docker-ecr-login
docker-ecr-login: $(call print-help,docker-ecr-login [AWS_PROFILE=<profile_name>],"Login to ECR")
	docker login -u AWS -p "$(shell aws ecr get-login-password)" https://${WB_ACC_NUM}.dkr.ecr.us-east-1.amazonaws.com

.PHONY: docker-push-ecr
docker-push-ecr: docker-ecr-login $(call print-help,docker-push-ecr,\
	                           "Push the image tagged with the \
                                    current git revision to ECR")
	@docker push ${FQ_TAG}

.PHONY: docker-tag
docker-tag: $(call print-help,docker-tag,\
	     "Tag the image with current git revision \
	      and ':latest' alias")
	@docker tag ${ECR_REPO_NAME}:${VERSION} ${FQ_TAG}
	@docker tag ${ECR_REPO_NAME}:${VERSION} \
		    ${WB_ACC_NUM}.dkr.ecr.us-east-1.amazonaws.com/${ECR_REPO_NAME}

.PHONY: eb-def-app-env
eb-def-app-env: $(call print-help,eb-def-app-env [WB_DB_URI=<datomic-db-uri>],\
	    "Define the ElasticBeanStalk app-environment config file.")
ifndef WB_DB_URI
	$(error WB_DB_URI not set! Define datomic-DB-URI as WB_DB_URI arg)
endif
	@cp ebextensions-templates/${EB_APP_ENV_FILE} .ebextensions/
	@sed -i -r 's~(WB_DB_URI:\s+)".*"~\1"'"${WB_DB_URI}"'"~' .ebextensions/${EB_APP_ENV_FILE}

.PHONY: eb-create
eb-create: eb-def-app-env $(call print-help,eb-create [AWS(_EB)?_PROFILE=<profile_name>] \
		[AWS_IAM_UNAME=<iam_username>] [PROJ_NAME=<eb-env-name>] [WB_DB_URI=<datomic-db-uri>],\
	    "Create an ElasticBeanStalk environment using the Docker platform.")
	$(eval AWS_IAM_UNAME ?= $(shell test ${AWS_IAM_UNAME} && echo ${AWS_IAM_UNAME}\
	                             || aws --profile ${AWS_PROFILE} iam get-user --query "User.UserName"))
	@test ${AWS_IAM_UNAME} || (\
		echo "Failed to retrieve IAM user-name. Define IAM username as AWS_IAM_UNAME arg." \
		&& exit 1 \
	)
	@eb create ${PROJ_NAME} \
	        --region=us-east-1 \
	        --tags="CreatedBy=${AWS_IAM_UNAME},Role=RestAPI" \
	        --cname="${PROJ_NAME}"

.PHONY: eb-deploy
eb-deploy: eb-def-app-env $(call print-help,eb-deploy [PROJ_NAME=<eb-env-name>] \
		[AWS_EB_PROFILE=<profile_name>] [WB_DB_URI=<datomic-db-uri>],\
		"Deploy the application using ElasticBeanstalk.")
	@eb deploy ${PROJ_NAME}

.PHONY: eb-env
eb-setenv: $(call print-help,eb-env [AWS_EB_PROFILE=<profile_name>] [WB_DB_URI=<datomic-uri>] [PROJ_NAME=<eb-env-name>],\
	     "Set enviroment variables for the ElasticBeanStalk environment")
	@eb setenv \
		WB_DB_URI="${WB_DB_URI}" \
		_JAVA_OPTIONS="-Xmx14g" \
		AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}" \
		AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}" \
		-e "${PROJ_NAME}"

.PHONY: eb-local
eb-local: docker-ecr-login $(call print-help,eb-local [AWS_EB_PROFILE=<profile_name>] [PORT=<port>] [WB_DB_URI=<datomic-uri>],\
			     "Runs the ElasticBeanStalk/docker \
			      build and run locally.")
	@eb local run --envvars PORT=${PORT},WB_DB_URI=${WB_DB_URI}

.PHONY: run
run: $(call print-help,run [WB_DB_URI=<datomic-uri>] [PORT=<port>] [PROJ_NAME=<docker-project-name>],"Run the application in docker (locally).")
	@docker run \
		--name ${PROJ_NAME} \
		--publish-all=true \
		--publish ${PORT}:${PORT} \
		--detach \
		-e AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY} \
		-e AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID} \
		-e WB_DB_URI=${WB_DB_URI} \
		-e PORT=${PORT} \
		${ECR_REPO_NAME}:${ARTIFACT_NAME}

.PHONY: docker-clean
docker-clean: $(call print-help,docker-clean [PROJ_NAME=<docker-project-name>],\
               "Stop and remove the docker container (if running).")
	@docker stop ${PROJ_NAME}
	@docker rm ${PROJ_NAME}

.PHONY: deploy-ecr
deploy-ecr: docker-build docker-ecr-login docker-tag docker-push-ecr
         $(call print-help,deploy-ecr\
                "Deploy the application to the AWS container registry.")

.PHONY: vc-release
vc-release: $(call print-help,vc-release LEVEL=<major|minor|patch>,"Perform the Version Control tasks to release the applicaton.")
	@echo "Edit version of application in pom.xml to match:"
	@clj -A:release --without-sign ${LEVEL}
	@clj -A:spit-version
	@clj -A:datomic-pro:prod:aws-eb-docker-version
	@rm resources/meta.edn


.PHONY: release
release: deploy-ecr $(call print-help,release [AWS_PROFILE=<profile_name>],"Release the applicaton.")
	@git archive ${ARTIFACT_NAME} -o target/app.zip
	@zip -u target/app.zip Dockerrun.aws.json

.PHONY: run-tests
run-tests: $(call print-help,run-tests,"Run all tests.")
	@clj -A:datomic-pro:webassets:dev:test:run-tests

.PHONY: run-dev-server
run-dev-webserver: $(call print-help,run-dev-webserver PORT=<port> WB_DB_URI=<datomic-uri>,"Run a development webserver.")
	@clj -A:logging:datomic-pro:webassets:dev -m wormbase.names.service
