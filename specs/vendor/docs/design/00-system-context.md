# 00 System Context

## Objective
Provide an automated trading platform that executes via IBKR with strong consistency guarantees and full operator visibility.

## Actors
- Strategy developers
- Trading operations
- Risk/compliance reviewers
- Engineering and SRE on-call

## External Systems
- IB Gateway / IBKR TWS API
- GitHub (code, actions, project tracking)
- Alert channels (email/Slack/Telegram)

## Hard Constraints
- No silent duplicate order execution.
- No unknown order state without immediate freeze.
- Dynamic policies must be adjustable without service redeploy.
- Every material action must be auditable.
