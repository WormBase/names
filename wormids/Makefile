target/wormids.jar: deps.edn src/**/*
	@clojure -Sdeps '{:deps {pack/pack.alpha {:git/url "https://github.com/juxt/pack.alpha.git" :sha "8acf80dd4d6e5173585f5c6fec7af28a310f3ed7"}}}' -m mach.pack.alpha.skinny --no-libs --project-path "$@"

pom.xml: deps.edn
	@clojure -Spom

release: target/wormids.jar pom.xml
	@mvn deploy:deploy-file -Dfile=target/wormids.jar -DrepositoryId=clojars -Durl=https://clojars.org/repo -DpomFile=pom.xml

clean:
	@rm -f target/wormids.jar pom.xml
