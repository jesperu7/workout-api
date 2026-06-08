# syntax=docker/dockerfile:1

# --- build: compile + package the executable jar -------------------------------------------
# Tests are NOT run here (they need a Docker-in-Docker Postgres via Testcontainers); run
# `./gradlew check` on the host instead. bootJar does not trigger tests.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
# Copy the wrapper + build scripts first so the dependency layer caches across src-only changes.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# --- runtime: slim JRE running the jar as a non-root user ----------------------------------
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
RUN useradd --system --uid 1001 appuser
COPY --from=build /app/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
