"""Health dashboard endpoints — polls containers and actuator endpoints."""
from __future__ import annotations

import json
import os
import subprocess
from pathlib import Path

import httpx
from fastapi import APIRouter
import psycopg2
import psycopg2.extras

router = APIRouter()

REPO_ROOT = Path(__file__).resolve().parent.parent.parent.parent.parent
COMPOSE_FILE = REPO_ROOT / "infra" / "local" / "docker-compose.yml"
COMPOSE_LOCAL_FILE = REPO_ROOT / "infra" / "local" / "docker-compose.local.yml"
ENV_FILE = REPO_ROOT / "infra" / "local" / ".env.compose.example"

APP_SERVICES = [
    {"name": "ingress-gateway-service", "port": 18080},
    {"name": "monitoring-api",           "port": 18084},
    {"name": "risk-service",             "port": 18081},
    {"name": "order-service",            "port": 18082},
    {"name": "ibkr-connector-service",   "port": 18083},
    {"name": "event-processor-service",  "port": 18085},
    {"name": "agent-runtime-service",    "port": 18086},
    {"name": "performance-service",      "port": 18087},
]

_DB_DEFAULTS = {
    "host": "localhost",
    "port": 5432,
    "dbname": os.environ.get("POSTGRES_DB", "autotrading"),
    "user": os.environ.get("POSTGRES_USER", "autotrading"),
    "password": os.environ.get("POSTGRES_PASSWORD", "autotrading_dev_only"),
}


def _conn():
    return psycopg2.connect(**_DB_DEFAULTS, cursor_factory=psycopg2.extras.RealDictCursor)


@router.get("/containers")
def get_containers():
    """All containers via docker compose ps."""
    result = subprocess.run(
        [
            "docker", "compose",
            "--env-file", str(ENV_FILE),
            "-f", str(COMPOSE_FILE),
            "-f", str(COMPOSE_LOCAL_FILE),
            "ps", "-a", "--format", "json",
        ],
        capture_output=True, text=True, cwd=str(REPO_ROOT),
    )
    containers = []
    for line in result.stdout.strip().splitlines():
        line = line.strip()
        if line:
            try:
                containers.append(json.loads(line))
            except json.JSONDecodeError:
                pass
    return {"containers": containers}


@router.get("/services")
async def get_service_health():
    """Poll /actuator/health/readiness on each app service."""
    results = []
    async with httpx.AsyncClient(timeout=2.0) as client:
        for svc in APP_SERVICES:
            url = f"http://localhost:{svc['port']}/actuator/health/readiness"
            try:
                resp = await client.get(url)
                data = resp.json()
                status = data.get("status", "UNKNOWN")
            except Exception:
                status = "DOWN"
            results.append({"service": svc["name"], "port": svc["port"], "status": status})
    return {"services": results}


@router.get("/broker")
async def get_broker_status():
    db_status = "UNKNOWN"
    db_updated_at = None
    db_detail_json = None

    try:
        with _conn() as conn, conn.cursor() as cur:
            cur.execute(
                "SELECT status, detail_json, updated_at FROM broker_health_status WHERE broker_id = 'ibkr'"
            )
            row = cur.fetchone()
            if row:
                db_status = row["status"]
                db_updated_at = row["updated_at"]
                db_detail_json = row["detail_json"]
    except Exception:
        db_status = "UNAVAILABLE"

    smoke_status = "DOWN"
    smoke_payload = {}
    try:
        async with httpx.AsyncClient(timeout=2.0) as client:
            resp = await client.get("http://localhost:18083/internal/smoke/stats")
            smoke_payload = resp.json() if resp.status_code == 200 else {}
            if resp.status_code == 200:
                smoke_status = "UP"
    except Exception:
        smoke_status = "DOWN"

    return {
        "brokerId": "ibkr",
        "healthTableStatus": db_status,
        "healthTableUpdatedAt": db_updated_at,
        "healthTableDetailJson": db_detail_json,
        "connectorStatus": smoke_status,
        "connectorStats": smoke_payload,
    }


@router.get("/activity")
def get_recent_activity(limit: int = 50):
    safe_limit = max(1, min(limit, 200))
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT
                ire.received_at,
                ire.ingress_event_id,
                ire.idempotency_key,
                ire.agent_id,
                ire.ingestion_status,
                oi.order_intent_id,
                ol.state AS trade_state,
                ol.last_status_at
            FROM ingress_raw_events ire
            LEFT JOIN order_intents oi ON oi.idempotency_key = ire.idempotency_key
            LEFT JOIN order_ledger ol ON ol.order_intent_id = oi.order_intent_id
            ORDER BY ire.received_at DESC
            LIMIT %s
            """,
            (safe_limit,),
        )
        rows = [dict(r) for r in cur.fetchall()]
    return {"items": rows, "limit": safe_limit}
