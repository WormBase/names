FROM openjdk:8-jre-alpine

ARG uberjar_path=
ADD $uberjar_path /srv/app.jar

# Expose necessary ports
EXPOSE 3000

CMD ["java", "-cp", "/srv/app.jar", "clojure.main", "-m", "wormbase.names.service"]
