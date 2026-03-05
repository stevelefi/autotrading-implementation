# Trading Platform Documentation Index

This folder contains the full project documentation for the broker-integrated trading platform reference design and the plan-sync automation tooling.

## Portal
- [Design Portal](./DESIGN_PORTAL.md)
- [Deep Design Index](./design/README.md)
- [Source of Truth Specification](./source-of-truth/README.md)

## Architecture and Design
- [Trading System Overview](./TRADING_SYSTEM_OVERVIEW.md)
- [Trading System Design Targets](./TRADING_SYSTEM_DESIGN_TARGETS.md)
- [Trading Architecture](./TRADING_ARCHITECTURE.md)
- [Trading Best Practices Baseline](./TRADING_BEST_PRACTICES_BASELINE.md)
- [Architecture Review Diagrams](./ARCHITECTURE_REVIEW_DIAGRAMS.md)
- [Sequence Diagrams](./design/04-sequence-diagrams.md)
- [Current Design Baseline](./current-design-baseline.md)
- [Components and Ownership](./COMPONENTS_AND_OWNERSHIP.md)
- [Order Consistency and Reconciliation](./ORDER_CONSISTENCY_AND_RECONCILIATION.md)
- [Service Contracts](./SERVICE_CONTRACTS.md)
- [Contracts Readiness and Extensibility](./CONTRACTS_READINESS_AND_EXTENSIBILITY.md)
- [Rule Engine (OPA)](./RULE_ENGINE_OPA.md)
- [Instruments and Markets (Stocks + MNQ)](./INSTRUMENTS_AND_MARKETS.md)
- [Production Plan (Detailed)](./PRODUCTION_PLAN.md)

## Technical Specs
- [Database Schema](./DATABASE_SCHEMA.md)
- [Kafka Event Contracts](./KAFKA_EVENT_CONTRACTS.md)
- [API Specification](./API_SPEC.md)
- [API Contract Validation](./API_CONTRACT_VALIDATION.md)
- [Policy Bundle Contract](./contracts/policy-bundle-contract.md)
- [Policy Decision Audit Contract](./contracts/policy-decision-audit-contract.md)

## Operations
- [Deployment and Environments](./DEPLOYMENT_AND_ENVIRONMENTS.md)
- [Spring Boot + Kubernetes Config Guide](./SPRINGBOOT_K8S_CONFIG_GUIDE.md)
- [Kubernetes Config and Secret Ownership Model](./K8S_CONFIG_OWNERSHIP_MODEL.md)
- [Documentation Website](./WEBSITE.md)
- [Docker-First DEV Workflow](./DEV_WORKFLOW_DOCKER.md)
- [IBKR Gateway Simulator (DEV)](./IBKR_GATEWAY_SIMULATOR.md)
- [Observability and Alerting](./OBSERVABILITY_AND_ALERTING.md)
- [Security and Compliance](./SECURITY_AND_COMPLIANCE.md)
- [Testing and Release Gates](./TESTING_AND_RELEASE_GATES.md)

## Delivery and Governance
- [Implementation Roadmap](./IMPLEMENTATION_ROADMAP.md)
- [Implementation Phases and Team Plan](./IMPLEMENTATION_PHASES.md)
- [Deliverables and Milestones Plan](./DELIVERABLES_AND_MILESTONES.md)
- [Spec Baseline Tagging and Multi-Agent Handoff](./SPEC_BASELINE_HANDOFF.md)
- [Task Tracking Guide](./TASK_TRACKING_GUIDE.md)
- [Automation Plan (GitHub/Java/Excel)](./IMPLEMENTATION_PLAN.md)
- [Plan Sync Ops Runbook](./runbooks/plan-sync-ops.md)
- [Trading Ops Runbook](./runbooks/trading-ops.md)

## Current Defaults
- Language/runtime: Java 21 + Spring Boot
- Broker: IBKR TWS API via IB Gateway
- Data store: PostgreSQL
- Event bus: Kafka (JSON contracts)
- Dynamic policies: OPA/Rego (fail-closed)
- Main consistency constraints:
  - idempotency key required for signal/order intent
  - 60s status deadline after submit
  - timeout => UNKNOWN_PENDING_RECON + trading freeze
  - resume only after reconciliation + operator ack

## Suggested Reading Order
1. `source-of-truth/README.md`
2. `source-of-truth/01-requirements.md`
3. `source-of-truth/02-reference-architecture.md`
4. `source-of-truth/04-service-contract-catalog.md`
5. `PRODUCTION_PLAN.md`
6. Team-specific guides in `design/teams/`
