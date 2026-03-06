#!/usr/bin/env python3
"""
trace.py — Query Loki for log lines matching autotrading MDC fields.

Examples
--------
  python3 scripts/trace.py --trace-id trc-abc-123
  python3 scripts/trace.py --idempotency-key k-abc-123
  python3 scripts/trace.py --agent-id agent-alpha --since 30m
  python3 scripts/trace.py --order-intent-id oi-xyz-789 --service order-service
  python3 scripts/trace.py --trace-id trc-abc-123 --level ERROR
  python3 scripts/trace.py --trace-id trc-abc-123 --json | jq '.[] | .line'
"""
from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any


# ---------------------------------------------------------------------------
# Time helpers
# ---------------------------------------------------------------------------

def _since_to_start_ns(since: str) -> int:
    """Convert '30m', '2h', '1d' to a Unix nanosecond timestamp (now - since)."""
    units = {"s": 1, "m": 60, "h": 3600, "d": 86400}
    suffix = since[-1]
    if suffix not in units:
        raise argparse.ArgumentTypeError(
            f"Invalid --since value '{since}'. Use formats like 30m, 2h, 1d."
        )
    seconds = int(since[:-1]) * units[suffix]
    start_sec = time.time() - seconds
    return int(start_sec * 1_000_000_000)


def _ns_to_iso(ns: int) -> str:
    dt = datetime.fromtimestamp(ns / 1_000_000_000, tz=timezone.utc)
    return dt.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


# ---------------------------------------------------------------------------
# Loki query
# ---------------------------------------------------------------------------

@dataclass
class LogEntry:
    ts_ns: int
    service: str
    line: str

    @property
    def iso_ts(self) -> str:
        return _ns_to_iso(self.ts_ns)


def _build_logql(args: argparse.Namespace) -> str:
    """Build a LogQL stream selector + filter expression from CLI args."""
    # Stream selector
    labels: dict[str, str] = {"compose_project": "autotrading"}
    if args.service:
        labels["service"] = args.service

    label_str = ",".join(f'{k}="{v}"' for k, v in labels.items())
    query = "{" + label_str + "}"

    # Line filters (|= "key=value")
    filters: list[str] = []

    if args.trace_id:
        filters.append(f"trace_id={args.trace_id}")
    if args.idempotency_key:
        filters.append(f"idempotency_key={args.idempotency_key}")
    if args.agent_id:
        filters.append(f"agent_id={args.agent_id}")
    if args.order_intent_id:
        filters.append(f"order_intent_id={args.order_intent_id}")
    if args.signal_id:
        filters.append(f"signal_id={args.signal_id}")
    if args.instrument_id:
        filters.append(f"instrument_id={args.instrument_id}")
    if args.request_id:
        filters.append(f"request_id={args.request_id}")

    if not filters:
        print("[trace.py] WARNING: no filters specified — querying all logs. Add at least one flag.", file=sys.stderr)

    for f in filters:
        query += f' |= "{f}"'

    if args.level:
        query += f' |= "{args.level.upper()}"'

    return query


def _loki_query_range(
    loki_url: str,
    query: str,
    start_ns: int,
    end_ns: int,
    limit: int = 5000,
    direction: str = "forward",
) -> list[dict[str, Any]]:
    """Call /loki/api/v1/query_range and return raw result streams."""
    params = urllib.parse.urlencode({
        "query": query,
        "start": start_ns,
        "end": end_ns,
        "limit": limit,
        "direction": direction,
    })
    url = f"{loki_url.rstrip('/')}/loki/api/v1/query_range?{params}"
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = resp.read().decode("utf-8")
            data = json.loads(body)
            return data.get("data", {}).get("result", [])
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8")
        print(f"[trace.py] Loki HTTP {exc.code}: {body[:300]}", file=sys.stderr)
        sys.exit(1)
    except urllib.error.URLError as exc:
        print(f"[trace.py] Cannot reach Loki at {loki_url}: {exc.reason}", file=sys.stderr)
        print("           Is the stack running? Try: make up", file=sys.stderr)
        sys.exit(1)


def _fetch_entries(args: argparse.Namespace) -> list[LogEntry]:
    start_ns = _since_to_start_ns(args.since)
    end_ns = int(time.time() * 1_000_000_000)
    query = _build_logql(args)

    if args.verbose:
        print(f"[trace.py] LogQL: {query}", file=sys.stderr)
        print(f"[trace.py] Range: {_ns_to_iso(start_ns)} → {_ns_to_iso(end_ns)}", file=sys.stderr)

    streams = _loki_query_range(
        loki_url=args.loki_url,
        query=query,
        start_ns=start_ns,
        end_ns=end_ns,
    )

    entries: list[LogEntry] = []
    for stream in streams:
        service = stream.get("stream", {}).get("service", "unknown")
        for ts_str, line in stream.get("values", []):
            entries.append(LogEntry(ts_ns=int(ts_str), service=service, line=line))

    entries.sort(key=lambda e: e.ts_ns)
    return entries


# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------

def _print_text(entries: list[LogEntry]) -> None:
    if not entries:
        print("[trace.py] No log lines matched.", file=sys.stderr)
        return
    max_svc = max(len(e.service) for e in entries)
    for entry in entries:
        svc = entry.service.ljust(max_svc)
        print(f"{entry.iso_ts}  {svc}  {entry.line}")


def _print_json(entries: list[LogEntry]) -> None:
    out = [{"ts": e.iso_ts, "ts_ns": e.ts_ns, "service": e.service, "line": e.line} for e in entries]
    print(json.dumps(out, indent=2))


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="Query Loki for autotrading log lines by MDC field.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )

    # Filter flags
    p.add_argument("--trace-id", metavar="ID", help="Filter by trace_id MDC field")
    p.add_argument("--idempotency-key", metavar="KEY", help="Filter by idempotency_key MDC field")
    p.add_argument("--agent-id", metavar="ID", help="Filter by agent_id MDC field")
    p.add_argument("--order-intent-id", metavar="ID", help="Filter by order_intent_id MDC field")
    p.add_argument("--signal-id", metavar="ID", help="Filter by signal_id MDC field")
    p.add_argument("--instrument-id", metavar="ID", help="Filter by instrument_id MDC field")
    p.add_argument("--request-id", metavar="ID", help="Filter by request_id MDC field")

    # Scope flags
    p.add_argument(
        "--service",
        metavar="NAME",
        help="Restrict to one service (e.g. risk-service). Default: all services.",
    )
    p.add_argument(
        "--level",
        metavar="LEVEL",
        help="Restrict to a log level: DEBUG, INFO, WARN, ERROR.",
    )

    # Time range
    p.add_argument(
        "--since",
        default="1h",
        metavar="DURATION",
        help="How far back to search. Formats: 30m, 2h, 1d. Default: 1h",
    )

    # Output
    p.add_argument(
        "--json",
        action="store_true",
        dest="json_output",
        help="Output JSON array instead of formatted text.",
    )
    p.add_argument(
        "--loki-url",
        default="http://localhost:3100",
        metavar="URL",
        help="Loki base URL. Default: http://localhost:3100",
    )
    p.add_argument("-v", "--verbose", action="store_true", help="Print LogQL and time range to stderr.")
    return p


def main() -> None:
    parser = _build_parser()
    args = parser.parse_args()

    entries = _fetch_entries(args)

    if args.json_output:
        _print_json(entries)
    else:
        _print_text(entries)


if __name__ == "__main__":
    main()
