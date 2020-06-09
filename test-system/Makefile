SHELL := /bin/bash
JAR_NAME := wb-names-test-system.jar
JAR := target/${JAR_NAME}

${JAR}: deps.edn src/**/*
	@mkdir -p target
	@clj -Spom
	@clj -A:uberjar ${JAR}

clean:
	@find ./target -name '*.jar' -delete

deploy:
	@aws s3 cp ${JAR} s3://wormbase/names/test-system/${JAR_NAME}
