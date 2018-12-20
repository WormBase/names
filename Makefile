NAME := wormbase/names
VERSION ?= $(shell git describe --abbrev=0 --tags)
EBX_CONFIG := .ebextensions/app-env.config
WB_DB_URI ?= $(shell sed -rn 's|value:(.*)|\1|p' \
                  ${EBX_CONFIG} | tr -d " " | head -n 1)
PROJ_NAME="wormbase-names"
RELEASE_NAME="${PROJ_NAME}-${VERSION}"
DEPLOY_JAR := app.jar
PORT := 3000
WB_ACC_NUM := 357210185381
FQ_TAG := ${WB_ACC_NUM}.dkr.ecr.us-east-1.amazonaws.com/${NAME}:${VERSION}

define print-help
        $(if $(need-help),$(warning $1 -- $2))
endef

need-help := $(filter help,$(MAKECMDGOALS))

help: ; @echo $(if $(need-help),,\
	Type \'$(MAKE)$(dash-f) help\' to get help)

docker/${DEPLOY_JAR}: $(call print-help,docker/${DEPLOY_JAR},\
		       "Build the jar file")
	@./scripts/build-appjar.sh

.PHONY: docker-ecr-login
docker-ecr-login: $(call print-help,docker-ecr-login,"Login to ECR")
	@eval $(shell aws ecr  get-login --no-include-email --registry-ids ${WB_ACC_NUM})

.PHONY: docker-tag
docker-tag: $(call print-help,docker-tag,\
	     "Tag the image with current git revision \
	      and ':latest' alias")
	@docker tag ${NAME}:${VERSION} ${FQ_TAG}
	@docker tag ${NAME}:${VERSION} \
		    ${WB_ACC_NUM}.dkr.ecr.us-east-1.amazonaws.com/${NAME}

.PHONY: docker-push-ecr
docker-push-ecr: docker-ecr-login $(call print-help,docker-push-ecr,\
	                           "Push the image tagged with the \
                                    current git revision to ECR")
	@docker push ${FQ_TAG}

.PHONY: eb-create
eb-create: $(call print-help,eb-create,\
	    "Create an ElasticBeanStalk environment using \
	     the Docker platofrm.")
	@eb create wormbase-names \
		--region=us-east-1 \
		--tags="CreatedBy=${AWS_EB_PROFILE},Role=RestAPI" \
		--cname="wormbase-names"

.PHONY: eb-env
eb-setenv: $(call print-help,eb-env,\
	     "Set enviroment variables for the \
	      ElasticBeanStalk environment")
	eb setenv WB_DB_URI="${WB_DB_URI}" \
		  _JAVA_OPTIONS="-Xmx14g" \
		  AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}" \
		  AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}" \
		  -e "${PROJ_NAME}"

.PHONY: eb-local
eb-local: docker-ecr-login $(call print-help,eb-local,\
			     "Runs the ElasticBeanStalk/docker \
			      build and run locally.")
	eb local run --envvars PORT=${PORT},WB_DB_URI=${WB_DB_URI}

.PHONY: build-client-app
build-client-app: $(call print-help,build-client-app,\
		    "Build the React Javascript client Application")
	@cd client && yarn install --frozen-lockfile && yarn build

.PHONY: build
build: clean \
       build-client-app \
       docker/${DEPLOY_JAR} \
       $(call print-help,build,\
	"Build the docker images from using the current git revision.")
	@docker build -t ${NAME}:${VERSION} \
		--build-arg uberjar_path=${DEPLOY_JAR} \
		--build-arg \
			aws_secret_access_key=${AWS_SECRET_ACCESS_KEY} \
		--build-arg \
			aws_access_key_id=${AWS_ACCESS_KEY_ID} \
		--rm ./docker/

.PHONY: run
run: $(call print-help,run,"Run the application in docker (locally).")
	docker run \
		--name ${PROJ_NAME} \
		--publish-all=true \
		--publish ${PORT}:${PORT} \
		--detach \
		-e AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY} \
		-e AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID} \
		-e WB_DB_URI=${WB_DB_URI} \
		-e PORT=${PORT} \
		${NAME}:${VERSION}

.PHONY: docker-build
docker-build: clean build \
              $(call print-help,docker-build, "Create docker container")

.PHONY: docker-clean
docker-clean: $(call print-help],docker-clean,\
               "Stop and remove the docker container (if running).")
	@docker stop ${PROJ_NAME}
	@docker rm ${PROJ_NAME}

.PHONY: clean
clean: $(call print-help,clean,"Remove the locally built JAR file.")
	@rm -f ./docker/${DEPLOY_JAR}

.PHONY: eb-deploy
	$(call print-help,eb-deploy,"Deploy the application using ElasticBeanstalk.")
	@eb use wormbase-names
	@eb deploy

