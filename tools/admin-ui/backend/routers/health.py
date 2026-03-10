"""Health dashboard endpoints — polls containers and actuator endpoints."""
from __future__ import annotations

import json
import subprocess
from pathlib import Path

import httpx
from fastapi import APIRouter

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
