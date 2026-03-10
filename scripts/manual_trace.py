#!/usr/bin/env python3
"""
manual_trace.py — Send an ingress event, capture the trace ID, tail Loki logs,
                   poll the IBKR pipeline, and print Grafana/Tempo UI links.

Usage examples:
  python3 scripts/manual_trace.py
  python3 scripts/manual_trace.py --agent-id my-agent --qty 5 --side SELL
  python3 scripts/manual_trace.py --client-event-id my-key-001 --no-browser
  python3 scripts/manual_trace.py --skip-pipeline-watch --loki-since 30m
  python3 scripts/manual_trace.py --token my-secret-token --skip-loki
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
import urllib.request
import urllib.error
import webbrowser
from datetime import datetime, timezone
from urllib.parse import urlencode

# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------
DEFAULT_INGRESS_URL   = "http://localhost:18080/ingress/v1/events"
DEFAULT_BROKER_STATS  = "http://localhost:18083/internal/smoke/stats"
DEFAULT_ORDER_URL     = "http://localhost:18082"
DEFAULT_MONITORING_URL = "http://localhost:18084"
DEFAULT_LOKI_URL      = "http://localhost:3100"
DEFAULT_GRAFANA_URL   = "http://localhost:3000"
DEFAULT_TEMPO_URL     = "http://localhost:3200"
PIPELINE_TIMEOUT_S   = 90
PIPELINE_POLL_S      = 3

# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="Send an ingress event and trace it end-to-end.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )

    # --- Ingress event fields ---
    g = p.add_argument_group("Ingress event")
    g.add_argument("--client-event-id", default=None,
                   help="Client event ID (default: auto-generated timestamp key)")
    g.add_argument("--event-intent", default="TRADE_SIGNAL", metavar="INTENT",
                   help="event_intent value (default: TRADE_SIGNAL)")
    g.add_argument("--agent-id", default="agent-smoke-pipeline", metavar="ID",
                   help="agent_id for Kafka partitioning (default: agent-smoke-pipeline; "
                        "must be owned by the account whose API key is used)")
    g.add_argument("--side", default="BUY", choices=["BUY", "SELL"],
                   help="payload.side (default: BUY)")
    g.add_argument("--qty", type=int, default=1, metavar="N",
                   help="payload.qty (default: 1)")
    g.add_argument("--token", default="smoke-api-key-local", metavar="TOKEN",
                   help="Raw API key for Authorization: Bearer header "
                        "(default: smoke-api-key-local — seeded by smoke_local.py Phase 0 "
                        "and onboard.py; use 'onboard.py apikey generate' to create a new key)")
    g.add_argument("--request-id", default=None, metavar="ID",
                   help="X-Request-Id header (default: auto-generated)")

    # --- Service URLs ---
    u = p.add_argument_group("Service URLs")
    u.add_argument("--ingress-url",    default=DEFAULT_INGRESS_URL)
    u.add_argument("--broker-stats",   default=DEFAULT_BROKER_STATS)
    u.add_argument("--order-url",      default=DEFAULT_ORDER_URL)
    u.add_argument("--monitoring-url", default=DEFAULT_MONITORING_URL)
    u.add_argument("--loki-url",       default=DEFAULT_LOKI_URL)
    u.add_argument("--grafana-url",    default=DEFAULT_GRAFANA_URL)
    u.add_argument("--tempo-url",      default=DEFAULT_TEMPO_URL)

    # --- Behaviour flags ---
    b = p.add_argument_group("Behaviour")
    b.add_argument("--skip-pipeline-watch", action="store_true",
                   help="Do not poll IBKR broker for submit confirmation")
    b.add_argument("--skip-reset", action="store_true",
                   help="Skip the order-service smoke reset (default: always reset before pipeline watch)")
    b.add_argument("--skip-loki", action="store_true",
                   help="Do not tail Loki logs")
    b.add_argument("--loki-since", default="5m", metavar="DURATION",
                   help="Loki lookback window passed to trace.py --since (default: 5m)")
    b.add_argument("--loki-delay", type=int, default=3, metavar="SECS",
                   help="Seconds to wait before querying Loki (let logs ingest, default: 3)")
    b.add_argument("--no-browser", action="store_true",
                   help="Print UI URLs but do not open them in a browser")
    b.add_argument("--verbose", "-v", action="store_true",
                   help="Print extra debug info (e.g. Loki LogQL queries)")
    return p

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _ts() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S")


def _print_section(title: str) -> None:
    width = 70
    print(f"\n{'=' * width}")
    print(f"  {title}")
    print(f"{'=' * width}")


def _http_get(url: str, timeout: int = 10) -> tuple[int, dict]:
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as exc:
        body = {}
        try:
            body = json.loads(exc.read())
        except Exception:
            pass
        return exc.code, body
    except urllib.error.URLError as exc:
        print(f"  [ERROR] Could not reach {url}: {exc.reason}", file=sys.stderr)
        return 0, {}


def _http_post(url: str, payload: dict, headers: dict, timeout: int = 15) -> tuple[int, dict]:
    data = json.dumps(payload).encode()
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    for k, v in headers.items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as exc:
        body = {}
        try:
            body = json.loads(exc.read())
        except Exception:
            pass
        return exc.code, body
    except urllib.error.URLError as exc:
        print(f"  [ERROR] Could not reach {url}: {exc.reason}", file=sys.stderr)
        return 0, {}

# ---------------------------------------------------------------------------
# Step 1 — Send the event
# ---------------------------------------------------------------------------

def send_event(args: argparse.Namespace) -> tuple[int, dict]:
    ts = _ts()
    client_event_id = args.client_event_id or f"debug-{ts}"
    request_id      = args.request_id      or f"req-{ts}"

    payload = {
        "client_event_id": client_event_id,
        "event_intent":    args.event_intent,
        "agent_id":        args.agent_id,
        "payload":         {"side": args.side, "qty": args.qty},
    }
    headers = {
        "Authorization": f"Bearer {args.token}",
        "X-Request-Id":  request_id,
    }

    _print_section("Sending Ingress Event")
    print(f"  URL:             {args.ingress_url}")
    print(f"  client_event_id: {client_event_id}")
    print(f"  event_intent:    {args.event_intent}")
    print(f"  agent_id:        {args.agent_id}")
    print(f"  payload:         side={args.side}, qty={args.qty}")
    print(f"  X-Request-Id:    {request_id}")

    status, body = _http_post(args.ingress_url, payload, headers)

    print(f"\n  HTTP Status: {status}")
    if body:
        print(f"  Response:\n{json.dumps(body, indent=4)}")

    return status, body

# ---------------------------------------------------------------------------
# Step 2 — Extract and display trace info
# ---------------------------------------------------------------------------

def extract_trace(status: int, body: dict) -> str | None:
    _print_section("Trace ID")

    if status == 0:
        print("  [SKIP] No response received from ingress service.")
        return None

    if status not in (200, 202):
        print(f"  [WARN] Unexpected status {status}. Trace ID may not be available.")

    trace_id = body.get("event_id")
    if trace_id:
        print(f"  event_id: {trace_id}")
    else:
        print("  [WARN] No event_id in response body.")

    return trace_id

# ---------------------------------------------------------------------------
# Step 3 — UI links
# ---------------------------------------------------------------------------

def show_ui_links(args: argparse.Namespace, trace_id: str | None, open_browser: bool) -> None:
    _print_section("Observability UI Links")

    grafana_dashboard = f"{args.grafana_url}/d/autotrading-reliability"
    prometheus_url    = f"http://localhost:9090"
    redpanda_url      = f"http://localhost:8081"

    links = [
        ("Grafana Reliability Dashboard", grafana_dashboard),
        ("Prometheus",                     prometheus_url),
        ("Redpanda Console (Kafka)",       redpanda_url),
    ]

    if trace_id:
        # Grafana Explore → Tempo datasource trace lookup
        explore_params = urlencode({
            "orgId": "1",
            "left": json.dumps({
                "datasource": "tempo",
                "queries":    [{"refId": "A", "query": trace_id}],
                "range":      {"from": "now-1h", "to": "now"},
            }),
        })
        grafana_trace_url = f"{args.grafana_url}/explore?{explore_params}"
        tempo_api_url     = f"{args.tempo_url}/api/traces/{trace_id}"
        links = [
            ("Grafana Trace (Tempo Explore)", grafana_trace_url),
            ("Tempo Trace API",               tempo_api_url),
        ] + links

    for label, url in links:
        print(f"  {label}:\n    {url}")

    if open_browser and trace_id:
        primary = links[0][1]
        print(f"\n  Opening in browser: {primary}")
        webbrowser.open(primary)
    elif open_browser:
        print(f"\n  Opening Grafana dashboard: {grafana_dashboard}")
        webbrowser.open(grafana_dashboard)

# ---------------------------------------------------------------------------
# Step 3.5 — Pre-pipeline health check
# ---------------------------------------------------------------------------

def check_and_reset_order_service(args: argparse.Namespace) -> str | None:
    """Check trading mode and reset order-service smoke state. Returns trading_mode string."""
    _print_section("Pre-Pipeline Check")

    # Check monitoring-api trading mode
    mode_status, mode_body = _http_get(f"{args.monitoring_url}/api/v1/system/consistency-status")
    trading_mode = mode_body.get("trading_mode", "UNKNOWN") if mode_status == 200 else "UNREACHABLE"
    kill_switch  = mode_body.get("kill_switch", "?")          if mode_status == 200 else "?"
    print(f"  Monitoring trading_mode: {trading_mode}   kill_switch: {kill_switch}")
    if trading_mode == "FROZEN":
        print("  [WARN] System is FROZEN — new orders will be rejected by order-service.")

    # Reset order-service smoke state (clears in-flight orders, sets TradingMode=NORMAL)
    if not args.skip_reset:
        reset_status, reset_body = _http_post(
            f"{args.order_url}/internal/smoke/reset", {}, {}
        )
        order_tm = reset_body.get("trading_mode", "UNKNOWN") if reset_status == 200 else "UNREACHABLE"
        print(f"  Order-service reset:     HTTP {reset_status}   trading_mode={order_tm}")
        if reset_status != 200:
            print("  [WARN] Order-service reset failed — pipeline watch may see stale baseline.")
    else:
        print("  Order-service reset:     skipped (--skip-reset)")

    return trading_mode


# ---------------------------------------------------------------------------
# Step 4 — Pipeline watch
# ---------------------------------------------------------------------------

def snapshot_broker_count(args: argparse.Namespace) -> int | None:
    """Capture broker submit count BEFORE sending the event to avoid race conditions."""
    status, body = _http_get(args.broker_stats)
    if status == 0:
        return None
    return body.get("total_submit_count", 0)


def watch_pipeline(args: argparse.Namespace, pre_send_count: int | None = None) -> bool:
    _print_section("Pipeline Watch — Waiting for IBKR Submit")
    print(f"  Polling: {args.broker_stats}")
    print(f"  Timeout: {PIPELINE_TIMEOUT_S}s (polling every {PIPELINE_POLL_S}s)\n")

    # Use pre-send snapshot if available; otherwise fall back to a fresh query.
    # The pre-send snapshot avoids a race where a fast pipeline (< ~200ms) completes
    # before this baseline query runs, causing the watch to miss the increment.
    if pre_send_count is not None:
        baseline_count = pre_send_count
        print(f"  Baseline total_submit_count: {baseline_count}  (captured before send)")
    else:
        status, baseline = _http_get(args.broker_stats)
        if status == 0:
            print("  [ERROR] Could not reach broker stats endpoint. Is the stack running?")
            return False
        baseline_count = baseline.get("total_submit_count", 0)
        print(f"  Baseline total_submit_count: {baseline_count}")

    # Diagnostic thresholds: check trading mode after 15s of no movement
    DIAG_AFTER_S   = 15
    diag_done      = False
    start_time     = time.time()

    deadline = time.time() + PIPELINE_TIMEOUT_S
    attempt  = 0
    while time.time() < deadline:
        time.sleep(PIPELINE_POLL_S)
        attempt += 1
        status, stats = _http_get(args.broker_stats)
        if status == 0:
            print(f"  [{attempt:>3}] Broker stats unreachable, retrying...")
            continue
        current = stats.get("total_submit_count", 0)
        elapsed = int(time.time() - start_time)
        print(f"  [{attempt:>3}] total_submit_count={current}  (+{current - baseline_count})  [{elapsed}s elapsed]")
        if current > baseline_count:
            print(f"\n  [PASS] Pipeline confirmed: IBKR submit count incremented to {current}.")
            return True

        # Mid-poll diagnostic after DIAG_AFTER_S with no movement
        if not diag_done and elapsed >= DIAG_AFTER_S:
            diag_done = True
            print(f"\n  [DIAG] Count unchanged for {elapsed}s — running diagnostics...")
            # Check trading mode
            m_status, m_body = _http_get(f"{args.monitoring_url}/api/v1/system/consistency-status")
            if m_status == 200:
                trading_mode = m_body.get("trading_mode", "UNKNOWN")
                kill_switch  = m_body.get("kill_switch", "?")
                marker = " <-- orders blocked!" if trading_mode == "FROZEN" else ""
                print(f"  [DIAG] trading_mode={trading_mode}  kill_switch={kill_switch}{marker}")
                if trading_mode == "FROZEN":
                    print("  [DIAG] Hint: 60s inactivity watchdog re-fired after reset.")
                    print("         Fix: POST http://localhost:18082/internal/smoke/reset to unfreeze,")
                    print("         then re-run with a fresh idempotency key.")
            else:
                print(f"  [DIAG] monitoring-api unreachable (HTTP {m_status})")
            # Check broker stats raw for extra context
            _, broker = _http_get(args.broker_stats)
            if broker:
                extra = {k: v for k, v in broker.items() if k != "total_submit_count"}
                if extra:
                    print(f"  [DIAG] broker stats extra fields: {extra}")
            print()

    print(f"\n  [FAIL] Timed out after {PIPELINE_TIMEOUT_S}s — IBKR submit count did not increase.")
    print("  Possible causes:")
    print("    1. trading_mode=FROZEN (60s watchdog re-fired) — POST /internal/smoke/reset on order-service")
    print("    2. Risk denial — check event-processor / risk-service logs: python3 scripts/trace.py --service risk-service")
    print("    3. Kafka consumer lag — check Redpanda console: http://localhost:8081")
    return False

# ---------------------------------------------------------------------------
# Step 5 — Tail Loki logs
# ---------------------------------------------------------------------------

def tail_loki(args: argparse.Namespace, trace_id: str) -> None:
    _print_section("Loki Logs (cross-service)")
    print(f"  trace_id: {trace_id}")
    print(f"  since:    --{args.loki_since}")
    print(f"  loki:     {args.loki_url}\n")

    cmd = [
        sys.executable,
        "scripts/trace.py",
        "--trace-id", trace_id,
        "--since",    args.loki_since,
        "--loki-url", args.loki_url,
    ]
    if args.verbose:
        cmd.append("--verbose")

    try:
        result = subprocess.run(cmd, check=False)
    except FileNotFoundError:
        print("  [ERROR] scripts/trace.py not found. Run from repo root.", file=sys.stderr)

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    parser = build_parser()
    args   = parser.parse_args()
    open_browser = not args.no_browser

    print(f"\nautotrading manual-trace  |  {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

    # Capture broker baseline BEFORE sending so a sub-200ms pipeline doesn't race past it
    pre_send_count = None
    if not args.skip_pipeline_watch:
        pre_send_count = snapshot_broker_count(args)

    # 1. Send event
    status, body = send_event(args)

    # 2. Extract trace ID
    trace_id = extract_trace(status, body)

    # 3. UI links (always show; optionally open browser)
    show_ui_links(args, trace_id, open_browser)

    # 3.5 Pre-pipeline: check trading mode and reset order-service
    trading_mode = "SKIPPED"
    if not args.skip_pipeline_watch and status in (200, 202):
        trading_mode = check_and_reset_order_service(args) or "UNKNOWN"

    # 4. Pipeline watch
    pipeline_ok = None
    if not args.skip_pipeline_watch:
        if status in (200, 202):
            pipeline_ok = watch_pipeline(args, pre_send_count=pre_send_count)
        else:
            print("\n  [SKIP] Pipeline watch skipped (event not accepted).")

    # 5. Loki log tail
    if not args.skip_loki:
        if trace_id:
            if args.loki_delay > 0:
                print(f"\n  Waiting {args.loki_delay}s for Loki to ingest logs...")
                time.sleep(args.loki_delay)
            tail_loki(args, trace_id)
        else:
            print("\n  [SKIP] Loki tail skipped — no trace_id available.")

    # --- Final summary ---
    _print_section("Summary")
    status_str  = f"HTTP {status}" if status else "UNREACHABLE"
    accepted    = status in (200, 202)
    pipe_str    = ("PASS" if pipeline_ok else "FAIL") if pipeline_ok is not None else "SKIPPED"
    loki_str    = "ran" if (not args.skip_loki and trace_id) else "skipped"

    print(f"  Ingress:          {status_str}  ({'accepted' if accepted else 'not accepted'})")
    print(f"  trace_id:         {trace_id or 'n/a'}")
    print(f"  Trading mode:     {trading_mode}")
    print(f"  Pipeline watch:   {pipe_str}")
    print(f"  Loki tail:        {loki_str}")
    print()

    if not accepted or pipeline_ok is False:
        sys.exit(1)


if __name__ == "__main__":
    main()
