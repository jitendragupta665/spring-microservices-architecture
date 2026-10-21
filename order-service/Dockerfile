# syntax=docker/dockerfile:1
# ---------- Stage 1: build the JAR (needs the full JDK + Maven) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
# Cache mount keeps ~/.m2 between builds on the same machine, so dependencies are
# downloaded once, not on every build. Tests run here: a failing test fails the build.
# The /root/.m2 cache mount is shared by builds that run in parallel (4 CI runners).
# Maven's default lock only works inside one JVM; file-lock + file-gav lock each
# artifact on disk so two builds never write the same file at the same time.
RUN --mount=type=cache,target=/root/.m2 mvn -q -Daether.syncContext.named.factory=file-lock -Daether.syncContext.named.nameMapper=file-gav package

# ---------- Stage 2: runtime image (only a JRE + the JAR) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
# Run as an unprivileged user instead of root
RUN useradd --system --uid 10001 app
COPY --from=build /src/target/*.jar app.jar
USER 10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
