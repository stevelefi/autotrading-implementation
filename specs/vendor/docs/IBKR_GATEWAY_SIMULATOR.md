# IBKR Gateway Simulator (Reference Only)

This repository is documentation-first and no longer contains a runnable simulator implementation.
Use this page as an interface reference for architecture and contract planning only.

## Scope
- Defines the expected simulator behavior used by architecture/design documents.
- Runtime simulator code and deployment are maintained outside this repository.

## Expected Simulator Surface
- Health endpoint: `GET /health`
- Order submit endpoint: `POST /v1/orders`
- Cancel endpoint: `POST /v1/orders/{ib_order_id}/cancel`
- Event stream endpoint: `GET /v1/events?since_id=0&limit=200`
- Reset endpoint: `POST /v1/admin/reset`

## Mapping to Platform Contracts
| Simulator Event | Intended Internal Topic | Notes |
|---|---|---|
| `orders.status` | `orders.status.v1` | contains `ib_order_id`, `perm_id`, `status`, `filled_qty`, `remaining_qty` |
| `fills.executed` | `fills.executed.v1` | contains `exec_id`, `fill_qty`, `fill_price`, order identifiers |

## Integration Guidance
- Keep simulator mode constrained to local/dev/CI workflows in runtime repositories.
- Keep contract compatibility aligned with [IBKR Connector Contract](./contracts/ibkr-connector-service.md).
