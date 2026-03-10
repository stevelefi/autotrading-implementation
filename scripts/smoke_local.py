#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import sys
import hashlib
import subprocess
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


@dataclass
class HttpResult:
    status: int
    body_text: str
    body_json: Any


def utc_ts() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def parse_json(text: str) -> Any:
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return None


def http_json(method: str, url: str, payload: dict[str, Any] | None = None, headers: dict[str, str] | None = None) -> HttpResult:
    data = None
    req_headers = {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        req_headers["Content-Type"] = "application/json"
    if headers:
        req_headers.update(headers)

    request = urllib.request.Request(url=url, data=data, headers=req_headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=10) as resp:
            body = resp.read().decode("utf-8")
            return HttpResult(resp.status, body, parse_json(body))
    except urllib.error.HTTPError as ex:
        body = ex.read().decode("utf-8")
        return HttpResult(ex.code, body, parse_json(body))


def wait_for_readiness(name: str, base_url: str, timeout_sec: int = 360) -> None:
    end = time.time() + timeout_sec
    last_status = "no response"
    print(f"  waiting for {name} ({base_url}) ...", flush=True)
    while time.time() < end:
        result = http_json("GET", f"{base_url}/actuator/health/readiness")
        status = result.status
        body_status = None
        if isinstance(result.body_json, dict):
            body_status = result.body_json.get("status")
        if status == 200 and body_status == "UP":
            print(f"  {name}: UP", flush=True)
            return
        last_status = f"status={status}, body={result.body_text[:180]}"
        elapsed = int(timeout_sec - (end - time.time()))
        print(f"  {name}: not ready ({last_status}) [{elapsed}s elapsed]", flush=True)
        time.sleep(2)
    raise RuntimeError(f"readiness timeout for {name}: {last_status}")


def ensure(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


# ---------------------------------------------------------------------------
# Smoke auth constants
# ---------------------------------------------------------------------------

SMOKE_RAW_KEY      = "smoke-api-key-local"
SMOKE_ACCOUNT_ID   = "acc-smoke"
SMOKE_AGENT_ID     = "agent-smoke"
SMOKE_PIPELINE_AGENT = "agent-smoke-pipeline"
SMOKE_LOAD_AGENT   = "agent-load"
OTHER_ACCOUNT_ID   = "acc-other-smoke"
OTHER_AGENT_ID     = "agent-other-smoke"


def _sha256_hex(raw: str) -> str:
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def _psql(sql: str) -> None:
    """
    Execute a SQL statement via docker exec in the local postgres container.
    Tolerates missing container (stack not up) by printing a warning.
    """
    container = os.getenv("POSTGRES_CONTAINER", "autotrading-local-postgres-1")
    db_user   = os.getenv("POSTGRES_USER",      "autotrading")
    db_pass   = os.getenv("POSTGRES_PASSWORD",  "autotrading_dev_only")
    db_name   = os.getenv("POSTGRES_DB",        "autotrading")
    result = subprocess.run(
        ["docker", "exec", "-i", container,
         "psql", "-U", db_user, "-d", db_name, "-c", sql],
        capture_output=True, text=True,
        env={**os.environ, "PGPASSWORD": db_pass},
    )
    if result.returncode != 0:
        raise RuntimeError(f"psql error: {result.stderr.strip()}")


def seed_smoke_auth_db() -> None:
    """
    Phase 0 — Seed the V10 auth tables with smoke-test fixtures.
    All inserts are idempotent (ON CONFLICT DO NOTHING / DO UPDATE).
    """
    key_hash = _sha256_hex(SMOKE_RAW_KEY)

    rows = [
        # accounts
        (f"INSERT INTO accounts (account_id, display_name, active, created_at) VALUES "
         f"('{SMOKE_ACCOUNT_ID}','Smoke Test Account',TRUE,now()) ON CONFLICT DO NOTHING"),
        (f"INSERT INTO accounts (account_id, display_name, active, created_at) VALUES "
         f"('{OTHER_ACCOUNT_ID}','Other Smoke Account',TRUE,now()) ON CONFLICT DO NOTHING"),
        # agents — main smoke account
        (f"INSERT INTO agents (agent_id, account_id, display_name, active, created_at) VALUES "
         f"('{SMOKE_AGENT_ID}','{SMOKE_ACCOUNT_ID}','Smoke Agent',TRUE,now()) ON CONFLICT DO NOTHING"),
        (f"INSERT INTO agents (agent_id, account_id, display_name, active, created_at) VALUES "
         f"('{SMOKE_PIPELINE_AGENT}','{SMOKE_ACCOUNT_ID}','Smoke Pipeline Agent',TRUE,now()) ON CONFLICT DO NOTHING"),
        (f"INSERT INTO agents (agent_id, account_id, display_name, active, created_at) VALUES "
         f"('{SMOKE_LOAD_AGENT}','{SMOKE_ACCOUNT_ID}','Smoke Load Agent',TRUE,now()) ON CONFLICT DO NOTHING"),
        # agent owned by the OTHER account (used to test 403)
        (f"INSERT INTO agents (agent_id, account_id, display_name, active, created_at) VALUES "
         f"('{OTHER_AGENT_ID}','{OTHER_ACCOUNT_ID}','Other Account Agent',TRUE,now()) ON CONFLICT DO NOTHING"),
        # api key for the main smoke account
        (f"INSERT INTO account_api_keys (key_hash, account_id, generation, active, created_at) VALUES "
         f"('{key_hash}','{SMOKE_ACCOUNT_ID}',1,TRUE,now()) ON CONFLICT DO NOTHING"),
        # broker accounts
        ("INSERT INTO broker_accounts (broker_account_id, agent_id, broker_id, external_account_id, active, created_at) VALUES "
         f"('ba-smoke','{SMOKE_AGENT_ID}','ibkr','DU000001',TRUE,now()) "
         "ON CONFLICT (agent_id) DO UPDATE SET external_account_id=EXCLUDED.external_account_id, active=TRUE"),
        ("INSERT INTO broker_accounts (broker_account_id, agent_id, broker_id, external_account_id, active, created_at) VALUES "
         f"('ba-smoke-pipeline','{SMOKE_PIPELINE_AGENT}','ibkr','DU000002',TRUE,now()) "
         "ON CONFLICT (agent_id) DO UPDATE SET external_account_id=EXCLUDED.external_account_id, active=TRUE"),
        ("INSERT INTO broker_accounts (broker_account_id, agent_id, broker_id, external_account_id, active, created_at) VALUES "
         f"('ba-smoke-load','{SMOKE_LOAD_AGENT}','ibkr','DU000003',TRUE,now()) "
         "ON CONFLICT (agent_id) DO UPDATE SET external_account_id=EXCLUDED.external_account_id, active=TRUE"),
    ]
    for sql in rows:
        _psql(sql)


def phase_six_auth_checks(ingress_url: str, details: dict) -> list[str]:
    """
    Phase 6 — Auth edge-case checks:
      (a) missing Authorization header          → 400
      (b) non-Bearer Authorization header       → 400
      (c) unknown / revoked API key             → 401
      (d) valid key but agent from other acct   → 403
      (e) valid key + owned agent               → 202
    """
    print("\n=== Phase 6: auth edge-case checks ===", flush=True)

    valid_payload = {
        "client_event_id": f"smoke-auth-{utc_ts()}-ok",
        "event_intent":    "TRADE_SIGNAL",
        "agent_id":        SMOKE_AGENT_ID,
        "payload":         {"side": "BUY", "qty": 1},
    }
    valid_headers = {"Authorization": f"Bearer {SMOKE_RAW_KEY}",
                     "X-Request-Id":  "smoke-auth-ok"}

    # (a) no Authorization header at all → 400
    r_no_header = http_json(
        "POST", f"{ingress_url}/ingress/v1/events",
        {**valid_payload, "client_event_id": f"smoke-auth-{utc_ts()}-noauth"},
        {"X-Request-Id": "smoke-auth-noheader"},
    )
    ensure(r_no_header.status == 400,
           f"(a) missing auth header: expected 400 got {r_no_header.status}")
    print("  (a) missing Authorization header → 400 ✓", flush=True)

    # (b) wrong auth scheme (not Bearer) → 400
    r_bad_scheme = http_json(
        "POST", f"{ingress_url}/ingress/v1/events",
        {**valid_payload, "client_event_id": f"smoke-auth-{utc_ts()}-scheme"},
        {"Authorization": "Basic dXNlcjpwYXNz", "X-Request-Id": "smoke-auth-scheme"},
    )
    ensure(r_bad_scheme.status == 400,
           f"(b) bad auth scheme: expected 400 got {r_bad_scheme.status}")
    print("  (b) non-Bearer Authorization header → 400 ✓", flush=True)

    # (c) unknown / garbage key → 401
    r_unknown = http_json(
        "POST", f"{ingress_url}/ingress/v1/events",
        {**valid_payload, "client_event_id": f"smoke-auth-{utc_ts()}-unknown"},
        {"Authorization": "Bearer totally-unknown-key-xyz",
         "X-Request-Id": "smoke-auth-unknown"},
    )
    ensure(r_unknown.status == 401,
           f"(c) unknown key: expected 401 got {r_unknown.status}")
    print("  (c) unknown API key → 401 ✓", flush=True)

    # (d) valid key but agent belongs to different account → 403
    r_forbidden = http_json(
        "POST", f"{ingress_url}/ingress/v1/events",
        {**valid_payload,
         "client_event_id": f"smoke-auth-{utc_ts()}-forbidden",
         "agent_id": OTHER_AGENT_ID},
        {"Authorization": f"Bearer {SMOKE_RAW_KEY}",
         "X-Request-Id": "smoke-auth-forbidden"},
    )
    ensure(r_forbidden.status == 403,
           f"(d) agent ownership mismatch: expected 403 got {r_forbidden.status}")
    print("  (d) valid key + wrong-account agent → 403 ✓", flush=True)

    # (e) valid key + owned agent → 202
    r_ok = http_json(
        "POST", f"{ingress_url}/ingress/v1/events",
        valid_payload, valid_headers,
    )
    ensure(r_ok.status == 202,
           f"(e) valid auth: expected 202 got {r_ok.status}")
    print("  (e) valid key + owned agent → 202 ✓", flush=True)

    details["steps"]["auth_checks"] = {
        "no_header":  {"status": r_no_header.status},
        "bad_scheme": {"status": r_bad_scheme.status},
        "unknown_key": {"status": r_unknown.status},
        "forbidden":  {"status": r_forbidden.status},
        "ok":         {"status": r_ok.status, "event_id": (r_ok.body_json or {}).get("event_id")},
    }
    return [
        "Auth: missing header → 400",
        "Auth: non-Bearer scheme → 400",
        "Auth: unknown key → 401",
        "Auth: cross-account agent → 403",
        "Auth: valid key + owned agent → 202",
    ]


def wait_for_broker_submit_delta(broker_url: str, baseline: int, timeout_sec: int = 90) -> int:
    """Poll broker stats until total_submit_count exceeds baseline. Returns new count."""
    end = time.time() + timeout_sec
    print(f"  polling broker for submit delta (baseline={baseline}, timeout={timeout_sec}s) ...", flush=True)
    while time.time() < end:
        result = http_json("GET", f"{broker_url}/internal/smoke/stats")
        current = int(get_nested(result.body_json, "total_submit_count") or 0)
        elapsed = int(timeout_sec - (end - time.time()))
        print(f"  broker submit count={current} baseline={baseline} [{elapsed}s elapsed]", flush=True)
        if current > baseline:
            return current
        time.sleep(2)
    raise RuntimeError(
        f"async pipeline timeout: broker submit count did not increase above {baseline} within {timeout_sec}s"
    )


def wait_for_broker_count_stable(broker_url: str, window_sec: int = 4, poll_sec: int = 1, timeout_sec: int = 30) -> int:
    """Poll broker stats until the count stays unchanged for window_sec, then return it.
    Used to drain any in-flight async events before snapshotting a stable baseline."""
    end = time.time() + timeout_sec
    last_count: int = -1
    stable_since: float | None = None
    while time.time() < end:
        result = http_json("GET", f"{broker_url}/internal/smoke/stats")
        current = int(get_nested(result.body_json, "total_submit_count") or 0)
        if current != last_count:
            last_count = current
            stable_since = time.time()
        elif stable_since is not None and (time.time() - stable_since) >= window_sec:
            return current
        time.sleep(poll_sec)
    return last_count


def get_nested(obj: Any, *keys: str) -> Any:
    current = obj
    for key in keys:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


def write_reports(report_status: str, summary_lines: list[str], details: dict[str, Any], timestamp: str) -> Path:
    e2e_dir = Path("reports/blitz/e2e-results")
    drill_dir = Path("reports/blitz/drill-logs")
    e2e_dir.mkdir(parents=True, exist_ok=True)
    drill_dir.mkdir(parents=True, exist_ok=True)

    md_path = e2e_dir / f"smoke-local-{timestamp}.md"
    json_path = drill_dir / f"smoke-local-{timestamp}.json"

    md = [
        f"# Smoke Local ({report_status})",
        "",
        f"- Timestamp (UTC): `{timestamp}`",
    ]
    for line in summary_lines:
        md.append(f"- {line}")
    md.append("")
    md.append(f"- Raw detail log: `{json_path.as_posix()}`")

    md_path.write_text("\n".join(md) + "\n", encoding="utf-8")
    json_path.write_text(json.dumps(details, indent=2) + "\n", encoding="utf-8")
    return md_path


def main() -> int:
    ts = utc_ts()
    details: dict[str, Any] = {"timestamp_utc": ts, "steps": {}}
    summaries: list[str] = []

    ingress_url = os.getenv("SMOKE_INGRESS_URL", "http://localhost:18080")
    risk_url = os.getenv("SMOKE_RISK_URL", "http://localhost:18081")
    order_url = os.getenv("SMOKE_ORDER_URL", "http://localhost:18082")
    broker_url = os.getenv("SMOKE_BROKER_URL", "http://localhost:18083")
    event_url = os.getenv("SMOKE_EVENT_URL", "http://localhost:18085")
    agent_url = os.getenv("SMOKE_AGENT_URL", "http://localhost:18086")
    perf_url = os.getenv("SMOKE_PERFORMANCE_URL", "http://localhost:18087")
    monitoring_url = os.getenv("SMOKE_MONITORING_URL", "http://localhost:18084")

    services = {
        "ingress": ingress_url,
        "event-processor": event_url,
        "agent-runtime": agent_url,
        "risk": risk_url,
        "order": order_url,
        "ibkr-connector": broker_url,
        "performance": perf_url,
        "monitoring": monitoring_url,
    }

    try:
        print("\n=== Phase 0: seed auth DB ===", flush=True)
        seed_smoke_auth_db()
        print("  smoke auth fixtures seeded (accounts, agents, keys, broker-accounts)", flush=True)

        print("\n=== Phase 1: service readiness ===", flush=True)
        for name, url in services.items():
            wait_for_readiness(name, url)
        summaries.append("All service readiness probes returned UP")
        print("  all services UP", flush=True)

        print("\n=== Phase 2: ingress idempotency ===", flush=True)
        ingress_idempotency_key = f"smoke-idem-{ts}"
        payload = {
            "client_event_id": ingress_idempotency_key,
            "event_intent": "TRADE_SIGNAL",
            "agent_id": SMOKE_AGENT_ID,
            "payload": {"side": "BUY", "qty": 1},
        }

        first = http_json("POST", f"{ingress_url}/ingress/v1/events", payload, {
            "Authorization": f"Bearer {SMOKE_RAW_KEY}",
            "X-Request-Id": "smoke-ingress-1",
        })
        second = http_json("POST", f"{ingress_url}/ingress/v1/events", payload, {
            "Authorization": f"Bearer {SMOKE_RAW_KEY}",
            "X-Request-Id": "smoke-ingress-2",
        })

        conflict_payload = {
            "client_event_id": ingress_idempotency_key,
            "event_intent": "TRADE_SIGNAL",
            "agent_id": SMOKE_AGENT_ID,
            "payload": {"side": "BUY", "qty": 2},
        }
        third = http_json("POST", f"{ingress_url}/ingress/v1/events", conflict_payload, {
            "Authorization": f"Bearer {SMOKE_RAW_KEY}",
            "X-Request-Id": "smoke-ingress-3",
        })

        details["steps"]["ingress"] = {
            "first": {"status": first.status, "body": first.body_json or first.body_text},
            "second": {"status": second.status, "body": second.body_json or second.body_text},
            "third": {"status": third.status, "body": third.body_json or third.body_text},
        }

        ensure(first.status == 202, f"expected first ingress status 202, got {first.status}")
        ensure(second.status == 202, f"expected second ingress status 202, got {second.status}")
        first_id = (first.body_json or {}).get("event_id")
        second_id = (second.body_json or {}).get("event_id")
        ensure(first_id is not None and first_id == second_id, "expected replay event_id to match")
        ensure(third.status == 202, f"expected first-write-wins ingress status 202, got {third.status}")
        summaries.append("Ingress idempotency replay/first-write-wins checks passed")
        print("  ingress idempotency passed", flush=True)

        print("\n=== Phase 3: risk->order->broker command path ===", flush=True)
        order_reset = http_json("POST", f"{order_url}/internal/smoke/reset", {})
        details["steps"]["order_reset"] = {
            "status": order_reset.status,
            "body": order_reset.body_json or order_reset.body_text,
        }
        ensure(order_reset.status == 200, f"expected order reset status 200, got {order_reset.status}")
        ensure(get_nested(order_reset.body_json, "trading_mode") == "NORMAL", "expected order reset trading mode NORMAL")

        risk_idempotency_key = f"smoke-command-{ts}"
        risk_payload = {
            "client_event_id": risk_idempotency_key,
            "signal_id": f"sig-smoke-{ts}",
            "qty": 1,
            "side": "BUY",
        }
        # Wait for any in-flight async events from Phase 2 to drain so the baseline is stable.
        print("  waiting for in-flight async events to drain before snapshot ...", flush=True)
        broker_before_count = wait_for_broker_count_stable(broker_url, window_sec=4, poll_sec=1, timeout_sec=30)
        broker_before = http_json("GET", f"{broker_url}/internal/smoke/stats")
        risk_first = http_json("POST", f"{risk_url}/internal/smoke/command-path", risk_payload)
        risk_second = http_json("POST", f"{risk_url}/internal/smoke/command-path", risk_payload)
        # Risk fires the order call asynchronously (fire-and-forget FutureStub), so poll
        # the broker stats rather than reading them immediately after risk returns.
        broker_after_count = wait_for_broker_submit_delta(broker_url, broker_before_count, timeout_sec=15)
        broker_stats = http_json("GET", f"{broker_url}/internal/smoke/stats")

        details["steps"]["command_path"] = {
            "broker_before": {"status": broker_before.status, "body": broker_before.body_json or broker_before.body_text},
            "risk_first": {"status": risk_first.status, "body": risk_first.body_json or risk_first.body_text},
            "risk_second": {"status": risk_second.status, "body": risk_second.body_json or risk_second.body_text},
            "broker_stats": {"status": broker_stats.status, "body": broker_stats.body_json or broker_stats.body_text},
        }

        ensure(risk_first.status == 200, f"expected risk first status 200, got {risk_first.status}")
        ensure(risk_second.status == 200, f"expected risk second status 200, got {risk_second.status}")
        ensure(get_nested(risk_first.body_json, "decision") == "DECISION_ALLOW", "expected risk first decision ALLOW")
        ensure(get_nested(risk_second.body_json, "decision") == "DECISION_ALLOW", "expected risk second decision ALLOW")
        submit_count_delta = broker_after_count - broker_before_count
        ensure(submit_count_delta == 1, f"expected broker submit delta 1 after retry, got {submit_count_delta}")
        summaries.append("Risk->Order->Broker command path retry dedupe passed")
        print("  command path passed", flush=True)

        print("\n=== Phase 4: 60s timeout freeze drill ===", flush=True)
        timeout_result = http_json("POST", f"{order_url}/internal/smoke/timeout-drill", {
            "client_event_id": f"smoke-timeout-{ts}",
        })
        order_stats = http_json("GET", f"{order_url}/internal/smoke/stats")

        details["steps"]["timeout_drill"] = {
            "timeout_result": {"status": timeout_result.status, "body": timeout_result.body_json or timeout_result.body_text},
            "order_stats": {"status": order_stats.status, "body": order_stats.body_json or order_stats.body_text},
        }

        ensure(timeout_result.status == 200, f"expected timeout drill status 200, got {timeout_result.status}")
        ensure(get_nested(timeout_result.body_json, "trading_mode") == "FROZEN", "expected trading mode FROZEN after timeout drill")
        ensure(get_nested(timeout_result.body_json, "order_state") == "UNKNOWN_PENDING_RECON", "expected UNKNOWN_PENDING_RECON order state")
        ensure(int(get_nested(timeout_result.body_json, "timeouts_triggered") or 0) >= 1, "expected at least one timeout to trigger")
        alerts = get_nested(timeout_result.body_json, "alerts")
        ensure(
            isinstance(alerts, list)
            and any(
                isinstance(alert, str)
                and "system.alerts.v1:CRITICAL:status_timeout_60s:" in alert
                for alert in alerts
            ),
            "expected critical timeout alert event on system.alerts.v1",
        )
        ensure(order_stats.status == 200, f"expected order stats status 200, got {order_stats.status}")
        ensure(get_nested(order_stats.body_json, "trading_mode") == "FROZEN", "expected order stats trading mode FROZEN")
        ensure(int(get_nested(order_stats.body_json, "first_status_timeout_count") or 0) >= 1, "expected timeout counter >= 1")
        ensure(int(get_nested(order_stats.body_json, "alert_count") or 0) >= 1, "expected alert count >= 1")
        summaries.append("60s timeout freeze + critical alert drill passed")
        print("  timeout drill passed", flush=True)

        # ------------------------------------------------------------------ #
        # Phase 5 — Full async Kafka pipeline                                 #
        # Ingress -> Kafka -> event-processor -> agent-runtime -> risk ->     #
        # order -> IBKR broker                                                #
        # ------------------------------------------------------------------ #
        print("\n=== Phase 5: full async Kafka pipeline ===", flush=True)
        pipeline_reset = http_json("POST", f"{order_url}/internal/smoke/reset", {})
        details["steps"]["pipeline_reset"] = {
            "status": pipeline_reset.status,
            "body": pipeline_reset.body_json or pipeline_reset.body_text,
        }
        ensure(pipeline_reset.status == 200, f"expected pipeline reset status 200, got {pipeline_reset.status}")
        ensure(
            get_nested(pipeline_reset.body_json, "trading_mode") == "NORMAL",
            "expected order-service trading mode NORMAL before pipeline test",
        )

        pipeline_broker_before = http_json("GET", f"{broker_url}/internal/smoke/stats")
        pipeline_broker_baseline = int(get_nested(pipeline_broker_before.body_json, "total_submit_count") or 0)

        pipeline_ingress_key = f"smoke-pipeline-{ts}"
        pipeline_ingress_payload = {
            "client_event_id": pipeline_ingress_key,
            "event_intent": "TRADE_SIGNAL",
            "agent_id": SMOKE_PIPELINE_AGENT,
            "payload": {"side": "BUY", "qty": 1},
        }
        pipeline_ingress = http_json(
            "POST",
            f"{ingress_url}/ingress/v1/events",
            pipeline_ingress_payload,
            {"Authorization": f"Bearer {SMOKE_RAW_KEY}", "X-Request-Id": "smoke-pipeline-1"},
        )
        details["steps"]["pipeline_ingress"] = {
            "status": pipeline_ingress.status,
            "body": pipeline_ingress.body_json or pipeline_ingress.body_text,
            "broker_baseline": pipeline_broker_baseline,
        }
        ensure(pipeline_ingress.status == 202, f"expected pipeline ingress status 202, got {pipeline_ingress.status}")

        pipeline_broker_final_count = wait_for_broker_submit_delta(
            broker_url, pipeline_broker_baseline, timeout_sec=90
        )
        pipeline_delta = pipeline_broker_final_count - pipeline_broker_baseline
        details["steps"]["pipeline_ingress"]["broker_final"] = pipeline_broker_final_count
        details["steps"]["pipeline_ingress"]["broker_delta"] = pipeline_delta
        ensure(pipeline_delta == 1, f"expected async pipeline broker delta 1, got {pipeline_delta}")
        summaries.append("Full async pipeline (Ingress->Kafka->event-processor->agent-runtime->risk->order->IBKR) passed")
        print("  async pipeline passed", flush=True)

        auth_summaries = phase_six_auth_checks(ingress_url, details)
        summaries.extend(auth_summaries)
        print("  auth checks passed", flush=True)

        report = write_reports("PASS", summaries, details, ts)
        print(f"\nsmoke-local PASS -> {report}")
        return 0

    except Exception as ex:  # noqa: BLE001
        summaries.append(f"FAILED: {ex}")
        details["error"] = str(ex)
        report = write_reports("FAIL", summaries, details, ts)
        print(f"smoke-local FAIL -> {report}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
