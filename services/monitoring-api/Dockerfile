FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml /workspace/pom.xml
COPY libs /workspace/libs
COPY services /workspace/services
COPY db /workspace/db
COPY tests /workspace/tests

RUN mvn -B -pl services/monitoring-api -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update     && apt-get install -y --no-install-recommends curl     && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/services/monitoring-api/target/monitoring-api-0.1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
