# Adding a New Service

Use this prompt when you need to create a new Spring Boot microservice in this monorepo.

---

## Pre-Implementation Checklist

- [ ] Read `specs/vendor/docs/repo-charters/autotrading-implementation.md` — confirm the new
  service is within scope of this repo's charter
- [ ] Check `SPEC_VERSION.json` — does the spec already define this service's API/topic contracts?
- [ ] Assign a port (see port map below)
- [ ] Blitz contract freeze: if the service requires a new HTTP endpoint, Kafka topic, or gRPC
  proto, raise a spec-change request before writing code

---

## Port Map (existing services)

| Service | HTTP | gRPC |
|---------|------|------|
| ingress-gateway-service | 8080 | — |
| event-processor-service | 8081 | — |
| agent-runtime-service | 8082 | — |
| risk-service | 8083 | 19091 |
| order-service | 8084 | 19092 |
| ibkr-connector-service | 8085 | 19093 |
| performance-service | 8086 | — |
| monitoring-api | 8087 | — |

Pick the next available HTTP port (8088+) and gRPC port (19094+) for the new service.

---

## Step 1 — Maven Module

Create `services/my-new-service/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.autotrading</groupId>
        <artifactId>autotrading-parent</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <artifactId>my-new-service</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.autotrading</groupId>
            <artifactId>reliability-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.autotrading</groupId>
            <artifactId>contracts</artifactId>
        </dependency>
        <!-- spring-boot-starter-web, kafka, jdbc, etc. as needed -->
    </dependencies>
</project>
```

Add `<module>services/my-new-service</module>` to the root `pom.xml`.

---

## Step 2 — Spring Boot Application Class

```java
@SpringBootApplication
public class MyNewServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyNewServiceApplication.class, args);
    }
}
```

---

## Step 3 — Configuration Properties

`src/main/resources/application.yml`:

```yaml
server:
  port: 8088

spring:
  application:
    name: my-new-service
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/autotrading}
    username: ${DB_USERNAME:autotrading}
    password: ${DB_PASSWORD:autotrading}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: my-new-service-consumer-group
      auto-offset-reset: earliest

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true

logging:
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg [%X]%n"
```

---

## Step 4 — SmartLifecycle Registration

If the service needs a warm cache or background probe before accepting traffic, implement
`SmartLifecycle` in the relevant `@Component` or `@Service`:

```java
@Component
public class MyCache implements SmartLifecycle {
    private volatile boolean running = false;

    @Override public int  getPhase()    { return 60; }  // pick between 50 and 200
    @Override public boolean isRunning() { return running; }

    @Override
    public void start() {
        running = true;
        // start background refresh thread
    }

    @Override
    public void stop() {
        running = false;
        // stop thread
    }
}
```

Phase ordering reminder:
- 40: `ApiKeyAuthenticator`, `BrokerAccountCache`
- 50: `BrokerHealthCache`
- 100: `IbkrHealthProbe`
- **60–90**: your new cache / probe (before Tomcat at 200+)
- 200+: Tomcat / gRPC

---

## Step 5 — Database Tables (if needed)

Create `db/migrations/V<max+1>__my_new_service_tables.sql` following the conventions in
`.github/prompts/new-flyway-migration.prompt.md`.

Key points:
- All tables in the shared `autotrading` database — there is no per-service DB
- No cross-service foreign keys (Rule 6 — `V6__drop_cross_service_fk.sql`)
- Add new tables to `README.md` "Tables by service" section

---

## Step 6 — Dockerfile

Copy from an existing service (e.g. `services/monitoring-api/Dockerfile`) and update:

```dockerfile
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY target/my-new-service-*.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Create `Dockerfile.local` for the local `restart-app` workflow:

```dockerfile
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY target/my-new-service-*.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## Step 7 — docker-compose Registration

In `infra/local/docker-compose.yml`, add:

```yaml
my-new-service:
  build:
    context: ../../services/my-new-service
    dockerfile: Dockerfile.local
  image: my-new-service:local
  ports:
    - "8088:8088"
  environment:
    DB_URL: jdbc:postgresql://postgres:5432/autotrading
    DB_USERNAME: autotrading
    DB_PASSWORD: autotrading
    KAFKA_BOOTSTRAP_SERVERS: redpanda:9092
    OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4318
    OTEL_SERVICE_NAME: my-new-service
  depends_on:
    flyway-init:
      condition: service_completed_successfully
    redpanda-init:
      condition: service_completed_successfully
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8088/actuator/health/readiness"]
    interval: 10s
    timeout: 5s
    retries: 36
    start_period: 60s
```

Update `scripts/stack.py` APP_SERVICES list to include `my-new-service`.

---

## Step 8 — OTel / Observability Wiring

Add to `application.yml`:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}/v1/traces
```

Add `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter` to `pom.xml`.

---

## Step 9 — Health Readiness Check

`scripts/smoke_local.py` Phase 1 checks all 8 services. Update the service list in
`smoke_local.py` and `scripts/test.py` (if the service count constant is hardcoded).

---

## Step 10 — Helm Chart (for production)

Add a Helm deployment manifest in `infra/helm/charts/trading-service/templates/`:

```yaml
# templates/my-new-service-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-new-service
spec:
  replicas: {{ .Values.myNewService.replicas | default 1 }}
  # ...
```

Add values to `values.yaml`:
```yaml
myNewService:
  image:
    repository: my-new-service
    tag: ""   # always driven by CI — never hard-code
  replicas: 1
```

Validate:
```bash
helm lint infra/helm/charts/trading-service
helm template trading-service infra/helm/charts/trading-service \
  -f infra/helm/charts/trading-service/values.yaml > /dev/null
```

---

## Step 11 — README Update

- Add the new service to the services table in `README.md`
- Add it to the "Tables by service" row in the Database section
- Add it to the System Flow ASCII diagram

---

## Checklist

- [ ] Maven module created and added to root `pom.xml`
- [ ] Application class with `main()` method
- [ ] `application.yml` with port, datasource, kafka, management
- [ ] `Dockerfile` + `Dockerfile.local`
- [ ] `docker-compose.yml` entry with healthcheck
- [ ] OTel / tracing configured
- [ ] `scripts/stack.py` APP_SERVICES updated
- [ ] `scripts/smoke_local.py` service count / URL map updated
- [ ] Flyway migration for any new tables (if needed)
- [ ] Helm chart entry (if production deployment)
- [ ] `README.md` updated
- [ ] Unit tests with `≥ 50%` line coverage
- [ ] `python3 scripts/check.py` all green
- [ ] `python3 scripts/smoke_local.py` all 6 phases pass
