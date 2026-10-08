# syntax=docker/dockerfile:1.7

# ---- build ------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Resolve dependencies in their own layer so source changes don't re-download them.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# ---- runtime ----------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S dispatch && adduser -S dispatch -G dispatch
WORKDIR /app
COPY --from=build /workspace/target/dispatch-api.jar app.jar
USER dispatch

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
