# 13 Disaster Recovery and BCP

## Disaster Classes
- Regional infra outage
- Broker connectivity outage
- Data corruption incident

## BCP Priorities
1. Preserve correctness of order ledger.
2. Freeze new opening risk when uncertain.
3. Restore observability and reconciliation tooling.

## DR Requirements
- Verified backup restore for Postgres.
- Runbook for rebuilding read models from ledger.
- Tested recovery time and recovery point objectives.
