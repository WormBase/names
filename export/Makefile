SHELL := /bin/bash
JAR_NAME := wb-names-export.jar
JAR := target/${JAR_NAME}

${JAR}: deps.edn src/**/*
	clj -A:pack mach.pack.alpha.capsule ${JAR} \
            -e build-dir \
            --application-id wb-names-export --application-version "$(git describe  --match '[0-9.]*')" \
            -m wormbase.names.export

pom.xml: deps.edn
	@clojure -Spom
	@sed -i 's|<groupId>ids</groupId>|<groupId>wormbase</groupId>|g' pom.xml

release: ${JAR}
	@mvn deploy:deploy-file -Dfile=${JAR} -DrepositoryId=clojars -Durl=https://clojars.org/repo -DpomFile=pom.xml

clean:
	@find ./target -name '*.jar' -delete
	@rm -f ${JAR} pom.xml

deploy:
	@aws s3 cp ${JAR} s3://wormbase/names/exporter/${JAR_NAME}
