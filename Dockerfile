#
# Multi-stage build so `docker compose up --build` works from a fresh clone.
#

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 1337
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
