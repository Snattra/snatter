# Builds the Snatter server from source and packages it as a JVM image.
#
#   docker build -f server/Dockerfile -t snatter-server .
#
# The build context is the repository root, since the server compiles the
# contract in protocol/openapi.
#
# For a prebuilt target/quarkus-app directory, the generated Dockerfiles in
# src/main/docker are smaller and faster; this one exists so that nobody needs
# a local JDK or Maven to run the server.

# --- Build stage --------------------------------------------------------------
FROM docker.io/library/maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build/server

# Resolve dependencies first so they are cached independently of source changes.
COPY server/pom.xml .
RUN mvn -B -q dependency:go-offline

COPY protocol/openapi ../protocol/openapi
COPY server/src ./src
RUN mvn -B -q package -DskipTests

# --- Runtime stage ------------------------------------------------------------
FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:1.24

ENV LANGUAGE='en_US:en'

# Four layers so library layers are reused when only application code changes.
COPY --from=build --chown=185 /build/server/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185 /build/server/target/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185 /build/server/target/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185 /build/server/target/quarkus-app/quarkus/ /deployments/quarkus/

# The database and uploaded content. Mount a volume here.
USER 0
RUN mkdir -p /var/lib/snatter && chown 185:0 /var/lib/snatter
ENV SNATTER_STORAGE_ROOT=/var/lib/snatter

# HTTP, and voice over UDP.
EXPOSE 8080 8080/udp
USER 185
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
