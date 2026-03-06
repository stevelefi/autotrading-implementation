FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml /workspace/pom.xml
COPY libs /workspace/libs
COPY services /workspace/services
COPY db /workspace/db
COPY tests /workspace/tests

RUN mvn -B -pl services/risk-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

ARG OTEL_JAVAAGENT_VERSION=2.13.1
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && mkdir -p /otel \
    && curl -fsSL -o /otel/opentelemetry-javaagent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVAAGENT_VERSION}/opentelemetry-javaagent.jar \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/services/risk-service/target/risk-service-0.1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
