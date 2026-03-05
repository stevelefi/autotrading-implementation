# 15 Third-Party Dependencies

## Critical External Dependencies
- Interactive Brokers TWS API / IB Gateway
- GitHub API (plan sync and workflows)

## Dependency Risks
- API change or outage
- auth token expiry/scope issues
- rate limits and throttling

## Controls
- retry with backoff
- explicit timeout and circuit-breakers
- monitoring and incident runbooks per dependency
