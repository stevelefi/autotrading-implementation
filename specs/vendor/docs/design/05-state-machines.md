# 05 State Machines

## Order State Machine
- `CREATED`
- `RISK_APPROVED`
- `SUBMIT_PENDING`
- `SUBMITTED_ACKED`
- `PARTIALLY_FILLED`
- `FILLED`
- `CANCELED`
- `REJECTED_BROKER`
- `UNKNOWN_PENDING_RECON`

Terminal states:
- `FILLED`, `CANCELED`, `REJECTED_BROKER`, `UNKNOWN_PENDING_RECON` (until reconciled)

## Trading Mode State Machine
- `NORMAL`
- `FROZEN`

Transitions:
- `NORMAL -> FROZEN`: uncertainty, timeout, reconciliation mismatch, kill switch
- `FROZEN -> NORMAL`: reconciliation clean + operator acknowledgment
