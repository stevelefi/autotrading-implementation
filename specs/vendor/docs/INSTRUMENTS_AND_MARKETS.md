# Instruments and Markets (Stocks + MNQ)

## Supported Asset Scope
- Equities: approved symbols from DB whitelist.
- Futures: MNQ only.

## Instrument Master
Each tradable product is represented by `instrument_id` with fields:
- `asset_type` (`EQUITY` or `FUTURE`)
- `symbol`
- `exchange`
- `currency`
- `multiplier`
- `tick_size`
- `status`
- IB contract metadata

## Equities Rules
- Reject non-whitelisted symbols.
- Exchange route defined in instrument config.

## MNQ Rules
- Trade front-month MNQ contract.
- Auto-roll using configured rollover window.
- If contract resolution is uncertain, freeze new MNQ opening orders.

## PnL Logic
- Equity PnL: `(exit - entry) * shares`.
- Futures PnL: `(exit - entry) * contracts * multiplier`.
