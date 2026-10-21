# syntax=docker/dockerfile:1
# ---------- Stage 1: build the JAR (needs the full JDK + Maven) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# The shared parent POM (Java/Spring versions) lives outside this folder. It arrives through a
# second build context named "parent" (docker build --build-context parent=./shop-parent ...)
# and lands at ../shop-parent, where <relativePath> in pom.xml points.
COPY --from=parent pom.xml /shop-parent/pom.xml
COPY pom.xml .
COPY src ./src
# Cache mount keeps ~/.m2 between builds on the same machine, so dependencies are
# downloaded once, not on every build. Tests run here: a failing test fails the build.
# The /root/.m2 cache mount is shared by builds that run in parallel (4 CI runners).
# Maven's default lock only works inside one JVM; file-lock + file-gav lock each
# artifact on disk so two builds never write the same file at the same time.
RUN --mount=type=cache,target=/root/.m2 mvn -q -Daether.syncContext.named.factory=file-lock -Daether.syncContext.named.nameMapper=file-gav package

# ---------- Export-only stage: test coverage report ----------
# CI runs `docker build --target coverage --output type=local,dest=...` to copy the CSV out.
# The build stage is reused from cache, so this costs seconds. The runtime image below
# (the default, last stage) never contains it.
FROM scratch AS coverage
COPY --from=build /src/target/site/jacoco/jacoco.csv /jacoco.csv

# ---------- Stage 2: runtime image (only a JRE + the JAR) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
# Run as an unprivileged user instead of root
RUN useradd --system --uid 10001 app
COPY --from=build /src/target/*.jar app.jar
USER 10001
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
