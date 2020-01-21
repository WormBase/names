FROM openjdk:8-jre-alpine

ARG uberjar_path=
ADD $uberjar_path /srv/app.jar

CMD ["java", "-cp", "/srv/app.jar", "clojure.main", "-m", "wormbase.names.service"]
