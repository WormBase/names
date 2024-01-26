ARG WORKDIR=/wb_names
ARG APPJAR_BUILDPATH=build/wb-names-app.jar

### Stage 1: build UI
FROM node:12 AS BUILD_UI_STAGE

ARG WORKDIR

WORKDIR $WORKDIR
COPY Makefile ./
COPY client/ ./client/
COPY scripts/ ./scripts/

RUN --mount=type=secret,id=make-secrets-file,required=true \
    make build-ui SECRETS_SRC=/run/secrets/make-secrets-file APP_PROFILE=prod

### Stage 2: build API (and include UI components)
FROM clojure:temurin-8-tools-deps-jammy as BUILD_API_STAGE

ARG WORKDIR
ARG APPJAR_BUILDPATH

RUN apt update && apt upgrade -y && apt install -y maven unzip

#Install clojure manually (com.datomic/datomic-pro 1.0.6165 is not stored on maven central)
COPY build/datomic-pro-1.0.6165.zip datomic-pro-1.0.6165.zip
RUN unzip datomic-pro-1.0.6165.zip \
     && cd datomic-pro-1.0.6165/ \
     && bin/maven-install

WORKDIR $WORKDIR
COPY Makefile ./
COPY src/wormbase/ src/wormbase/
COPY resources/ ./resources/
COPY deps.edn ./
COPY project.clj ./
COPY scripts/ ./scripts/
COPY --from=BUILD_UI_STAGE $WORKDIR/client/ $WORKDIR/client/

RUN make build-app-jar APP_JAR_PATH=$APPJAR_BUILDPATH

### Stage 3: build final application image
FROM openjdk:8-jre-alpine as APPLICATION_IMAGE_STAGE

ARG WORKDIR
ARG APPJAR_BUILDPATH

RUN apk update && apk upgrade

COPY --from=BUILD_API_STAGE $WORKDIR/$APPJAR_BUILDPATH /srv/wb-names-app.jar

# Expose necessary ports
EXPOSE 3000

CMD ["java", "-cp", "/srv/wb-names-app.jar", "clojure.main", "-m", "wormbase.names.service"]
