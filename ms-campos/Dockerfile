# syntax=docker/dockerfile:1

# ---- Etapa 1: compilar (agro-commons + el servicio) ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src

# agro-commons primero: el servicio depende de esta librería
COPY agro-commons/pom.xml agro-commons/pom.xml
COPY agro-commons/src agro-commons/src
RUN --mount=type=cache,target=/root/.m2 mvn -q -f agro-commons/pom.xml install -DskipTests

COPY ms-campos/pom.xml ms-campos/pom.xml
COPY ms-campos/src ms-campos/src
RUN --mount=type=cache,target=/root/.m2 mvn -q -f ms-campos/pom.xml package -DskipTests

# ---- Etapa 2: imagen final, solo la JRE y el jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/ms-campos/target/app.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
