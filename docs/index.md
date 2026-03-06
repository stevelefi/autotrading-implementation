# Autotrading Implementation

Welcome to the operational documentation hub for the autotrading backend platform.

This site covers the **running system** — how to build, run, debug, and operate the
8 Java microservices that implement the [Trading Platform Reference Design](https://stevelefi.github.io/autotrading/).

## Quick Navigation

| Document | What it covers |
|---|---|
| [Data Flow](DATA_FLOW.md) | End-to-end message path from signal ingestion to IBKR execution |
| [Implementation Instructions](IMPLEMENTATION_INSTRUCTIONS.md) | Local dev setup, service map, adding a new service |
| [Observability Guide](OBSERVABILITY.md) | Grafana, Loki, Prometheus, trace.py CLI, MDC field reference |
| [Reliability Drills](runbooks/reliability-drills.md) | Chaos + failover runbooks, recovery commands |

## System at a Glance

```
Signal Ingestion → Agent Runtime → Risk Engine → Order Service
                                                      │
                                            IBKR Connector ──→ Broker
                                                      │
                                         Performance Tracker
```

* **Services**: 8 Spring Boot 3 microservices, Java 21
* **Messaging**: Kafka (Redpanda) with transactional outbox/inbox
* **Persistence**: PostgreSQL with Flyway migrations
* **Observability**: Prometheus · Grafana · Loki · OpenTelemetry

## Spec Baseline

This implementation targets:
[`spec-v1.0.1-m0m1`](https://github.com/stevelefi/autotrading/tree/spec-v1.0.1-m0m1)
— see [SPEC_VERSION.json](https://github.com/stevelefi/autotrading-implementation/blob/main/SPEC_VERSION.json) for the pinned ref.
