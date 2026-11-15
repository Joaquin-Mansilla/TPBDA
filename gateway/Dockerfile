# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY gateway/pom.xml gateway/pom.xml
COPY gateway/src gateway/src
RUN --mount=type=cache,target=/root/.m2 mvn -q -f gateway/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/gateway/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
