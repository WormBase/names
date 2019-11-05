JAR := target/wbids.jar

clean:
	@rm -f ${JAR}

${JAR}: deps.edn src/**/*
	@clojure -A:depstar -m hf.depstar.jar ${JAR}

release: ${JAR}
	@mvn deploy:deploy-file -Dfile=${JAR} -DrepositoryId=clojars -Durl=https://clojars.org/repo -DpomFile=pom.xml
