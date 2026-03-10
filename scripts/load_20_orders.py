#!/usr/bin/env python3
"""
load_20_orders.py
Simulate 20 external customer trade-signal events flowing into the system via
the real ingress endpoint (POST /ingress/v1/events).  Each submission returns
an OTel trace_id which is stored in every downstream DB row, making the full
pipeline traceable in Grafana Tempo / Loki.

Flow: caller → ingress-gateway → Kafka → event-processor → agent-runtime
      → risk (gRPC) → order → ibkr-connector
"""
from __future__ import annotations
import json, time, uuid, threading, urllib.request, urllib.error
from datetime import datetime, timezone
from typing import Any

# ── Service base URLs ─────────────────────────────────────────────────────────
INGRESS_URL    = "http://localhost:18080"
RISK_URL       = "http://localhost:18081"
ORDER_URL      = "http://localhost:18082"
BROKER_URL     = "http://localhost:18083"
MONITORING_URL = "http://localhost:18084"
EVTPROC_URL    = "http://localhost:18085"
AGENT_URL      = "http://localhost:18086"
PERF_URL       = "http://localhost:18087"

TOTAL_ORDERS   = 20
BATCH_SIZE     = 4
AGENT_ID       = "agent-load"        # agent_id sent in every ingress request
AUTH_TOKEN     = "Bearer smoke-token"
W              = 132                 # report width

# ── HTTP helpers ──────────────────────────────────────────────────────────────

def post(url: str, body: dict, headers: dict | None = None) -> tuple[int, Any]:
    data = json.dumps(body).encode()
    h = {"Content-Type": "application/json"}
    if headers:
        h.update(headers)
    req = urllib.request.Request(url, data=data, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        try:    err = e.read().decode()
        except: err = ""
        return e.code, {"_error": err}
    except Exception as ex:
        return 0, {"_error": str(ex)}


def get(url: str) -> tuple[int, Any]:
    try:
        with urllib.request.urlopen(url, timeout=10) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        return e.code, {}
    except Exception as ex:
        return 0, {"_error": str(ex)}


def broker_count() -> int:
    _, b = get(f"{BROKER_URL}/internal/smoke/stats")
    return int(b.get("total_submit_count", 0))


def wait_for_broker(target: int, timeout: int = 90) -> int:
    """Wait until broker submit count reaches target (async pipeline can take ~30s)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        c = broker_count()
        if c >= target:
            return c
        time.sleep(2)
    return broker_count()


# ── Order submission (one thread per order) ───────────────────────────────────

def submit_order(idx: int, slot: list) -> None:
    """Submit one trade signal event through the real ingress endpoint."""
    idem        = f"load-{uuid.uuid4()}"
    request_id  = f"req-load-{uuid.uuid4()}"
    side        = "BUY" if idx % 2 == 0 else "SELL"
    qty         = (idx % 5) + 1
    t0          = time.time()
    sts, body   = post(
        f"{INGRESS_URL}/ingress/v1/events",
        {
            "client_event_id": idem,
            "event_intent":    "TRADE_SIGNAL",
            "agent_id":        AGENT_ID,
            "payload":         {"side": side, "qty": qty},
        },
        {
            "Authorization": AUTH_TOKEN,
            "X-Request-Id":  request_id,
        },
    )
    lat = int((time.time() - t0) * 1000)
    d   = body if isinstance(body, dict) else {}
    data  = d.get("data", {}) or {}
    slot.append({
        "n":                idx + 1,
        "idem":             idem,
        "side":             side,
        "qty":              qty,
        "trace_id":         d.get("trace_id", ""),   # OTel 32-char hex
        "event_id":         d.get("event_id", ""),
        "http":             sts,
        "status":           data.get("status", "?"),
        "lat_ms":           lat,
        "ok":               sts == 202,
    })


# ── Formatting ────────────────────────────────────────────────────────────────

def section(title: str) -> None:
    print(f"\n{'═' * W}\n  {title}\n{'═' * W}")

def col(v: Any, w: int) -> str:
    return str(v)[:w].ljust(w)

def kv(label: str, value: Any, indent: int = 4) -> None:
    print(f"{' '*indent}{label:<42}{value}")

def hr(c: str = "─", w: int = W) -> str:
    return c * w


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    run_ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    print(f"\n{'█'*W}\n  AUTOTRADING — 20-ORDER LOAD TEST & SYSTEM REPORT   run={run_ts}\n{'█'*W}")
    print(f"  Path: POST {INGRESS_URL}/ingress/v1/events  (real customer ingress — OTel trace IDs)\n")

    # ── 1. Readiness ──────────────────────────────────────────────────────────
    section("1 / PRE-FLIGHT — SERVICE READINESS")
    svcs = [
        ("ingress-gateway",     INGRESS_URL),
        ("risk-service",        RISK_URL),
        ("order-service",       ORDER_URL),
        ("ibkr-connector",      BROKER_URL),
        ("monitoring-api",      MONITORING_URL),
        ("event-processor",     EVTPROC_URL),
        ("agent-runtime",       AGENT_URL),
        ("performance-service", PERF_URL),
    ]
    all_up = True
    for name, base in svcs:
        sts, body = get(f"{base}/actuator/health/readiness")
        up = sts == 200 and (body or {}).get("status") == "UP"
        if not up: all_up = False
        print(f"  {'✅ UP' if up else '❌ DOWN':<8}  {name:<30}  {base}")
    if not all_up:
        print("\n  ⚠️  One or more services are DOWN — aborting."); return

    # ── 2. Reset ──────────────────────────────────────────────────────────────
    section("2 / RESET")
    sts, rbod = post(f"{ORDER_URL}/internal/smoke/reset", {})
    print(f"  POST /internal/smoke/reset  →  HTTP {sts}")
    kv("trading_mode",             rbod.get("trading_mode"))
    kv("alert_count",              rbod.get("alert_count"))
    kv("first_status_timeout_cnt", rbod.get("first_status_timeout_count"))
    baseline = broker_count()
    kv("IBKR baseline submit_count", baseline)

    # ── 3. Submit 20 events via real ingress ──────────────────────────────────
    section("3 / EVENT SUBMISSION — 20 trade signals · 5 batches of 4 parallel")
    print(f"  Agent: {AGENT_ID}  |  event_intent: TRADE_SIGNAL  |  Auth: {AUTH_TOKEN}")
    print(f"  NOTE: Pipeline is async (Kafka → agent-runtime → risk → order → ibkr).")
    print(f"        202 = accepted at ingress; broker delivery confirmed by polling.\n")
    print(f"  {'#':>3}  {'HTTP':>5}  {'STATUS':<12}  {'EVENT_ID':<40}  "
          f"{'SIDE':<5}  {'QTY':>4}  {'LAT_MS':>7}  OTEL_TRACE_ID")
    print(f"  {hr('-',3)}  {hr('-',5)}  {hr('-',12)}  {hr('-',40)}  "
          f"{hr('-',5)}  {hr('-',4)}  {hr('-',7)}  {hr('-',32)}")

    all_results: list[dict] = []
    batches = [list(range(i, min(i+BATCH_SIZE, TOTAL_ORDERS)))
               for i in range(0, TOTAL_ORDERS, BATCH_SIZE)]

    for b_idx, batch in enumerate(batches):
        slots: list[list] = [[] for _ in batch]
        threads = [threading.Thread(target=submit_order, args=(oi, slots[pos]))
                   for pos, oi in enumerate(batch)]
        for t in threads: t.start()
        for t in threads: t.join()
        for slot in slots:
            if slot:
                r = slot[0]
                all_results.append(r)
                print(f"  {'✅' if r['ok'] else '❌'}{r['n']:>2}  "
                      f"{r['http']:>5}  {col(r['status'],12)}  "
                      f"{col(r['event_id'],40)}  "
                      f"{r['side']:<5}  {r['qty']:>4}  {r['lat_ms']:>7}  {r['trace_id']}")
        print(f"  {'':>3}  ── batch {b_idx+1}/{len(batches)} submitted ──")

    accepted = sum(1 for r in all_results if r["ok"])
    print(f"\n  Waiting for async pipeline: target broker_delta={accepted} (timeout=90s) …")
    final_count = wait_for_broker(baseline + accepted, timeout=90)
    print(f"  Pipeline settled: broker_count={final_count}  delta={final_count - baseline}")

    # ── 4. Per-order detail table ─────────────────────────────────────────────
    section("4 / PER-ORDER DETAIL TABLE")
    print(f"  {'#':>3}  {'SIDE':<5}  {'QTY':>4}  {'HTTP':>5}  {'STATUS':<12}  "
          f"{'LAT_MS':>7}  {'EVENT_ID':<40}  CLIENT_EVENT_ID")
    print(f"  {hr('-',3)}  {hr('-',5)}  {hr('-',4)}  {hr('-',5)}  {hr('-',12)}  "
          f"{hr('-',7)}  {hr('-',40)}  {hr('-',38)}")
    for r in all_results:
        print(f"  {'✅' if r['ok'] else '❌'}{r['n']:>2}  "
              f"{r['side']:<5}  {r['qty']:>4}  "
              f"{r['http']:>5}  {col(r['status'],12)}  {r['lat_ms']:>7}  "
              f"{col(r['event_id'],40)}  {r['idem']}")

    # ── 5. Post-run system snapshot ───────────────────────────────────────────
    section("5 / POST-RUN SYSTEM SNAPSHOT")

    sts_r, r_stats = get(f"{RISK_URL}/internal/smoke/stats")
    print(f"\n  ▶ RISK  /internal/smoke/stats  HTTP {sts_r}")
    kv("policy_audit_event_count", r_stats.get("policy_audit_event_count","?"))
    kv("timestamp_utc",            r_stats.get("timestamp_utc","?"))

    sts_o, o_stats = get(f"{ORDER_URL}/internal/smoke/stats")
    print(f"\n  ▶ ORDER  /internal/smoke/stats  HTTP {sts_o}")
    kv("trading_mode",              o_stats.get("trading_mode","?"))
    kv("first_status_timeout_count",o_stats.get("first_status_timeout_count","?"))
    kv("alert_count",               o_stats.get("alert_count","?"))
    kv("timestamp_utc",             o_stats.get("timestamp_utc","?"))

    sts_b, b_stats = get(f"{BROKER_URL}/internal/smoke/stats")
    print(f"\n  ▶ IBKR  /internal/smoke/stats  HTTP {sts_b}")
    kv("total_submit_count", b_stats.get("total_submit_count","?"))
    kv("broker_delta",       f"{final_count - baseline}  (baseline={baseline} → final={final_count})")
    kv("timestamp_utc",      b_stats.get("timestamp_utc","?"))

    sts_cs, cs = get(f"{MONITORING_URL}/api/v1/system/consistency-status")
    print(f"\n  ▶ MONITORING  /api/v1/system/consistency-status  HTTP {sts_cs}")
    kv("trading_mode", cs.get("trading_mode","?"))
    kv("kill_switch",  cs.get("kill_switch","?"))
    kv("updated_at",   cs.get("updated_at","?"))

    sts_al, al_body = get(f"{MONITORING_URL}/api/v1/system/alerts")
    alerts = al_body.get("alerts", []) if isinstance(al_body, dict) else []
    print(f"\n  ▶ MONITORING  /api/v1/system/alerts  HTTP {sts_al}  ({len(alerts)} alert(s))")
    if alerts:
        print(f"  {'SEV':<12} {'SOURCE':<35} {'MESSAGE':<50} RECEIVED_AT")
        print(f"  {hr('-',12)} {hr('-',35)} {hr('-',50)} {hr('-',28)}")
        for a in alerts:
            print(f"  {col(a.get('severity',''),12)} {col(a.get('source',''),35)} "
                  f"{col(a.get('message',''),50)} {a.get('receivedAt','')}")
    else:
        print("    (no alerts)")

    # ── 6. Risk QA trace snapshots (first 5) ──────────────────────────────────
    section("6 / RISK QA — OTel trace snapshots (first 5 orders)")
    print("  NOTE: trace_id values are OTel 32-char hex — same ID visible in Grafana Tempo.")
    print("        Requires qa.api.enabled=true in the service environment.\n")
    for r in [x for x in all_results if x.get("trace_id")][:5]:
        tid = r["trace_id"]
        sts, snap = get(f"{RISK_URL}/internal/qa/snapshot?traceId={tid}")
        decs = snap.get("risk_decisions", [])      if isinstance(snap, dict) else []
        logs = snap.get("policy_decision_log", []) if isinstance(snap, dict) else []
        print(f"\n  Order #{r['n']}  otel_trace_id={tid}  (HTTP {sts})")
        if sts == 404:
            print("    (QA API disabled — set qa.api.enabled=true to enable)")
            continue
        if decs:
            d = decs[0]
            kv("decision",        d.get("decision","?"))
            kv("policy_version",  d.get("policy_version","?"))
            kv("failure_mode",    d.get("failure_mode","?"))
        if logs:
            lg = logs[0]
            kv("pdl decision",    lg.get("decision","?"))
            kv("pdl latency_ms",  lg.get("latency_ms","?"))
            kv("pdl agent_id",    lg.get("agent_id","?"))

    # ── 7. IBKR QA broker_orders (aggregate by agent) ─────────────────────────
    section(f"7 / IBKR QA — broker_orders for agent_id={AGENT_ID}")
    print("  NOTE: signal_id is generated server-side by agent-runtime (not caller-controlled).")
    print("        Querying IBKR QA by agentId to show all broker orders for this load run.\n")
    sts_ibkr, ibkr_qa = get(f"{BROKER_URL}/internal/qa/state?agentId={AGENT_ID}")
    print(f"  GET /internal/qa/state?agentId={AGENT_ID}  →  HTTP {sts_ibkr}")
    if sts_ibkr == 404:
        print("  (QA API disabled — set qa.api.enabled=true to enable)")
    elif isinstance(ibkr_qa, dict):
        orders  = ibkr_qa.get("broker_orders", [])
        execs   = ibkr_qa.get("executions", [])
        kv("broker_orders found",  len(orders))
        kv("executions found",     len(execs))
        if orders:
            print(f"\n  {'BROKER_ORDER_ID':<40} {'SIGNAL_ID':<40} {'STATUS':<16} SIDE / QTY")
            print(f"  {hr('-',40)} {hr('-',40)} {hr('-',16)} {hr('-',10)}")
            for bo in orders[:10]:
                print(f"  {col(bo.get('broker_order_id',''),40)} "
                      f"{col(bo.get('signal_id',''),40)} "
                      f"{col(bo.get('status',''),16)} "
                      f"{bo.get('side','')} / {bo.get('qty','')}")
            if len(orders) > 10:
                print(f"  … and {len(orders)-10} more")

    # ── 8. Performance QA positions ───────────────────────────────────────────
    section(f"8 / PERFORMANCE QA — positions for agent_id={AGENT_ID}")
    sts, psnap = get(f"{PERF_URL}/internal/qa/state?agentId={AGENT_ID}")
    positions = psnap.get("positions", [])     if isinstance(psnap, dict) else []
    snapshots = psnap.get("pnl_snapshots", []) if isinstance(psnap, dict) else []
    print(f"  GET /internal/qa/state?agentId={AGENT_ID}  →  HTTP {sts}")
    if sts == 404:
        print("  (QA API disabled — set qa.api.enabled=true to enable)")
    elif positions:
        print(f"\n  {'INSTRUMENT':<22} {'QTY':>12} {'AVG_COST':>14} {'REALIZED_PNL':>16}")
        print(f"  {hr('-',22)} {hr('-',12)} {hr('-',14)} {hr('-',16)}")
        for p in positions:
            print(f"  {col(p.get('instrument_id',''),22)} "
                  f"{col(p.get('qty',''),12)} "
                  f"{col(p.get('avg_cost',''),14)} "
                  f"{col(p.get('realized_pnl',''),16)}")
    else:
        kv("positions", "(none — positions accumulate after fill events)")
    kv("pnl_snapshots", len(snapshots))

    # ── 9. Final summary ──────────────────────────────────────────────────────
    section("9 / FINAL SUMMARY")
    rejected  = TOTAL_ORDERS - accepted
    delta     = final_count - baseline
    lats      = sorted(r["lat_ms"] for r in all_results)
    avg_lat   = int(sum(lats) / len(lats)) if lats else 0
    p99_lat   = lats[max(0, int(len(lats) * 0.99) - 1)]

    rows = [
        ("Submission path",                     f"POST /ingress/v1/events (real OTel ingress)"),
        ("Events submitted",                    TOTAL_ORDERS),
        ("Ingress 202 ACCEPTED",                f"{accepted} / {TOTAL_ORDERS}"),
        ("Ingress non-202 (rejected/error)",    f"{rejected} / {TOTAL_ORDERS}"),
        ("IBKR submit delta",                   f"{delta}  (baseline={baseline} → final={final_count})"),
        ("Ingress latency avg / min / max / p99", f"{avg_lat}ms / {lats[0]}ms / {lats[-1]}ms / {p99_lat}ms"),
        ("Order service trading_mode",          o_stats.get("trading_mode","?")),
        ("Order service alert_count",           o_stats.get("alert_count","?")),
        ("Order timeout_count",                 o_stats.get("first_status_timeout_count","?")),
        ("Monitoring trading_mode",             cs.get("trading_mode","?")),
        ("Monitoring kill_switch",              cs.get("kill_switch","?")),
        ("Monitoring alerts fired",             len(alerts)),
        ("Risk policy audit events",            r_stats.get("policy_audit_event_count","?")),
        ("OTel trace IDs",                      f"searchable in Grafana Tempo / scripts/trace.py --trace-id <id>"),
    ]
    print()
    for k, v in rows:
        print(f"  {k:<50}  {v}")

    ok = (delta >= accepted
          and o_stats.get("trading_mode") == "NORMAL"
          and cs.get("kill_switch") is False)
    print()
    if ok:
        print(f"  ✅  PASS — {delta}/{accepted} events reached IBKR · trading_mode=NORMAL · kill_switch=off")
    else:
        print(f"  ❌  FAIL — broker_delta={delta} (need>={accepted}), "
              f"mode={o_stats.get('trading_mode')}, kill={cs.get('kill_switch')}")
    print(f"\n{'█'*W}\n")


if __name__ == "__main__":
    main()
