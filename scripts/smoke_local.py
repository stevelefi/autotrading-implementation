#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import sys
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
    while time.time() < end:
        result = http_json("GET", f"{base_url}/actuator/health/readiness")
        status = result.status
        body_status = None
        if isinstance(result.body_json, dict):
            body_status = result.body_json.get("status")
        if status == 200 and body_status == "UP":
            return
        last_status = f"status={status}, body={result.body_text[:180]}"
        time.sleep(2)
    raise RuntimeError(f"readiness timeout for {name}: {last_status}")


def ensure(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


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
        for name, url in services.items():
            wait_for_readiness(name, url)
        summaries.append("All service readiness probes returned UP")

        ingress_idempotency_key = f"smoke-idem-{ts}"
        payload = {
            "idempotency_key": ingress_idempotency_key,
            "event_intent": "TRADE_SIGNAL",
            "agent_id": "agent-smoke",
            "payload": {"side": "BUY", "qty": 1},
        }

        first = http_json("POST", f"{ingress_url}/ingress/v1/events", payload, {
            "Authorization": "Bearer smoke-token",
            "X-Request-Id": "smoke-ingress-1",
        })
        second = http_json("POST", f"{ingress_url}/ingress/v1/events", payload, {
            "Authorization": "Bearer smoke-token",
            "X-Request-Id": "smoke-ingress-2",
        })

        conflict_payload = {
            "idempotency_key": ingress_idempotency_key,
            "event_intent": "TRADE_SIGNAL",
            "agent_id": "agent-smoke",
            "payload": {"side": "BUY", "qty": 2},
        }
        third = http_json("POST", f"{ingress_url}/ingress/v1/events", conflict_payload, {
            "Authorization": "Bearer smoke-token",
            "X-Request-Id": "smoke-ingress-3",
        })

        details["steps"]["ingress"] = {
            "first": {"status": first.status, "body": first.body_json or first.body_text},
            "second": {"status": second.status, "body": second.body_json or second.body_text},
            "third": {"status": third.status, "body": third.body_json or third.body_text},
        }

        ensure(first.status == 202, f"expected first ingress status 202, got {first.status}")
        ensure(second.status == 202, f"expected second ingress status 202, got {second.status}")
        first_id = get_nested(first.body_json, "data", "ingress_event_id")
        second_id = get_nested(second.body_json, "data", "ingress_event_id")
        ensure(first_id is not None and first_id == second_id, "expected replay ingress_event_id to match")
        ensure(third.status == 409, f"expected conflict ingress status 409, got {third.status}")
        summaries.append("Ingress idempotency replay/conflict checks passed")

        order_reset = http_json("POST", f"{order_url}/internal/smoke/reset", {})
        details["steps"]["order_reset"] = {
            "status": order_reset.status,
            "body": order_reset.body_json or order_reset.body_text,
        }
        ensure(order_reset.status == 200, f"expected order reset status 200, got {order_reset.status}")
        ensure(get_nested(order_reset.body_json, "trading_mode") == "NORMAL", "expected order reset trading mode NORMAL")

        risk_idempotency_key = f"smoke-command-{ts}"
        risk_payload = {
            "idempotency_key": risk_idempotency_key,
            "signal_id": f"sig-smoke-{ts}",
            "qty": 1,
            "side": "BUY",
        }
        broker_before = http_json("GET", f"{broker_url}/internal/smoke/stats")
        risk_first = http_json("POST", f"{risk_url}/internal/smoke/command-path", risk_payload)
        risk_second = http_json("POST", f"{risk_url}/internal/smoke/command-path", risk_payload)
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
        broker_before_count = int(get_nested(broker_before.body_json, "total_submit_count") or 0)
        broker_after_count = int(get_nested(broker_stats.body_json, "total_submit_count") or 0)
        submit_count_delta = broker_after_count - broker_before_count
        ensure(submit_count_delta == 1, f"expected broker submit delta 1 after retry, got {submit_count_delta}")
        summaries.append("Risk->Order->Broker command path retry dedupe passed")

        timeout_result = http_json("POST", f"{order_url}/internal/smoke/timeout-drill", {
            "idempotency_key": f"smoke-timeout-{ts}",
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

        report = write_reports("PASS", summaries, details, ts)
        print(f"smoke-local PASS -> {report}")
        return 0

    except Exception as ex:  # noqa: BLE001
        summaries.append(f"FAILED: {ex}")
        details["error"] = str(ex)
        report = write_reports("FAIL", summaries, details, ts)
        print(f"smoke-local FAIL -> {report}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
