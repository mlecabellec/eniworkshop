# Multi-stage OCI Containerfile compatible with Podman and Docker
FROM docker.io/library/maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline -B -Dskip.frontend=true
COPY checkstyle.xml pmd-ruleset.xml ./
COPY src ./src

# Build Svelte 5 frontend + Spring Boot runnable JAR (unit tests run separately in CI / test helper)
RUN mvn package -DskipTests -B

FROM docker.io/library/eclipse-temurin:21-jre-jammy AS runtime

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system eniworkshop \
    && useradd --system --gid eniworkshop --home /app eniworkshop

WORKDIR /app
COPY --from=build /app/target/eniworkshop-0.1.0-SNAPSHOT.jar /app/eniworkshop.jar
RUN chown -R eniworkshop:eniworkshop /app

USER eniworkshop

ENV PORT=8080
ENV ENIWORKSHOP_MONGO_URI=mongodb://mongodb:27017/eniworkshop
ENV QUASAR_OPCUA_ENDPOINT=opc.tcp://quasar:4840

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=25s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/eniworkshop.jar"]
