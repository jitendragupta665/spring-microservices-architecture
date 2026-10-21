# syntax=docker/dockerfile:1
# ---------- Stage 1: build the JAR (needs the full JDK + Maven) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
# Cache mount keeps ~/.m2 between builds on the same machine, so dependencies are
# downloaded once, not on every build. Tests run here: a failing test fails the build.
RUN --mount=type=cache,target=/root/.m2 mvn -q package

# ---------- Stage 2: runtime image (only a JRE + the JAR) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
# Run as an unprivileged user instead of root
RUN useradd --system --uid 10001 app
COPY --from=build /src/target/*.jar app.jar
USER 10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
