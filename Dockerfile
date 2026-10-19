# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
COPY docker/Healthcheck.java /health/Healthcheck.java
# Integration tests run separately with Docker available; the image build only packages.
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode --no-transfer-progress -Dmaven.test.skip=true package     && javac -d /health /health/Healthcheck.java

FROM eclipse-temurin:25-jre-noble AS runtime
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/volumes-0.1.0-SNAPSHOT.jar app.jar
COPY --from=build --chown=10001:10001 /health/Healthcheck.class /health/Healthcheck.class
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=6 CMD ["java", "-cp", "/health", "Healthcheck"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
