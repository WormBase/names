SHELL := /bin/bash
JAR_NAME := wb-names-export.jar
JAR := target/${JAR_NAME}

${JAR}: deps.edn src/**/*
	@clj -Spom
	@clj -A:datomic-pro:uberjar ${JAR}

clean:
	@find ./target -name '*.jar' -delete

deploy:
	@aws s3 cp ${JAR} s3://wormbase/names/exporter/${JAR_NAME}
