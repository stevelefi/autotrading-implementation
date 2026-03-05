# SRE Team Guide

## Scope
Owns runtime operations, observability stack, incident response, capacity planning, and deployment safety controls.

## Owned Components
- Compose and deployment pipelines
- Metrics/logs/traces stacks
- Alert routing and escalation

## Reliability Controls
- Health gates before enabling trading mode.
- Alert policies for timeout/freeze/unknown states.
- Resource and saturation monitoring for DB/Kafka/services.

## Change Management
- Controlled rollout windows.
- Automated rollback playbooks.
- Post-incident review requirements.

## Drills
- Connector outage drill
- Kafka outage drill
- Reconciliation failure drill
