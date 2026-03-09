#!/usr/bin/env python3
"""
Local stack manager — controls infra and app services independently.

Usage:
    python3 scripts/stack.py <command> [options]

Commands:
    infra-up        Start infrastructure services (postgres, redpanda, observability)
    infra-down      Stop and remove infrastructure services + volumes
    app-up          Start application microservices (requires infra to be up)
    app-down        Stop and remove application microservices only
    up              Start everything (infra + app)
    down            Stop and remove everything (volumes included)
    restart-app     Stop app, rebuild images, start app (infra stays up)
    build           Build all app Docker images
    status          Show running containers
    logs            Tail logs (optional: --service <name>)
    validate        Show status then run smoke suite
    ci              Full clean run: down -> build -> up -> validate -> app-down
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

# ── configuration ──────────────────────────────────────────────────────────────

ROOT = Path(__file__).resolve().parent.parent
ENV_FILE = ROOT / "infra/local/.env.compose.example"
COMPOSE_FILE = ROOT / "infra/local/docker-compose.yml"
# Local-dev overlay: uses Dockerfile.local (no Maven inside Docker)
COMPOSE_LOCAL_FILE = ROOT / "infra/local/docker-compose.local.yml"

INFRA_SERVICES = [
    "postgres",
    "flyway-init",
    "redpanda",
    "redpanda-init",
    "redpanda-console",
    "otel-collector",
    "prometheus",
    "loki",
    "promtail",
    "grafana",
    "ibkr-simulator",
]

APP_SERVICES = [
    "ingress-gateway-service",
    "event-processor-service",
    "agent-runtime-service",
    "risk-service",
    "order-service",
    "ibkr-connector-service",
    "performance-service",
    "monitoring-api",
]

# ── helpers ────────────────────────────────────────────────────────────────────

BASE_CMD = [
    "docker", "compose",
    "--env-file", str(ENV_FILE),
    "-f", str(COMPOSE_FILE),
]

# Build command: applies docker-compose.local.yml on top so services use
# Dockerfile.local (just copies the pre-built JAR — no Maven in Docker).
BUILD_CMD = [
    "docker", "compose",
    "--env-file", str(ENV_FILE),
    "-f", str(COMPOSE_FILE),
    "-f", str(COMPOSE_LOCAL_FILE),
]


def compose(*args: str, check: bool = True) -> int:
    cmd = BASE_CMD + list(args)
    print(f"\n$ {' '.join(cmd)}", flush=True)
    result = subprocess.run(cmd, cwd=ROOT)
    if check and result.returncode != 0:
        sys.exit(result.returncode)
    return result.returncode


def compose_build(*args: str, check: bool = True) -> int:
    """Like compose() but uses the local override (Dockerfile.local)."""
    cmd = BUILD_CMD + list(args)
    print(f"\n$ {' '.join(cmd)}", flush=True)
    result = subprocess.run(cmd, cwd=ROOT)
    if check and result.returncode != 0:
        sys.exit(result.returncode)
    return result.returncode


def banner(text: str) -> None:
    print(f"\n{'=' * 60}", flush=True)
    print(f"  {text}", flush=True)
    print(f"{'=' * 60}\n", flush=True)


# ── commands ───────────────────────────────────────────────────────────────────

def cmd_infra_up() -> None:
    banner("Starting infrastructure services")
    compose("up", "-d", "--remove-orphans", *INFRA_SERVICES)


def cmd_infra_down() -> None:
    banner("Stopping infrastructure services (volumes removed)")
    compose("down", "-v")


def cmd_app_up() -> None:
    banner("Starting application services")
    compose("up", "-d", "--remove-orphans", *APP_SERVICES)


def cmd_app_down() -> None:
    banner("Stopping application services")
    compose("stop", *APP_SERVICES)
    compose("rm", "-f", *APP_SERVICES)


def cmd_up() -> None:
    banner("Starting full stack (infra + app)")
    compose("up", "-d", "--remove-orphans")


def cmd_down() -> None:
    banner("Stopping full stack (volumes removed)")
    compose("down", "-v")


def cmd_build(services: list[str] | None = None) -> None:
    targets = services or APP_SERVICES
    banner(f"Building: {', '.join(targets)}")

    # Step 1: Maven build on the host — uses ~/.m2 cache, only recompiles
    # changed modules.  -am also rebuilds any libs/* the service depends on.
    mvn_modules = ",".join(f"services/{s}" for s in targets)
    mvn_cmd = [
        "mvn", "-B",
        "-pl", mvn_modules,
        "-am", "-DskipTests", "package",
    ]
    print(f"\n$ {' '.join(mvn_cmd)}", flush=True)
    result = subprocess.run(mvn_cmd, cwd=ROOT)
    if result.returncode != 0:
        sys.exit(result.returncode)

    # Step 2: Docker builds — each service just copies the pre-built JAR.
    # OTel layer is cached after first run; subsequent builds finish in ~1s/service.
    compose_build("build", *targets)


def cmd_restart_app(services: list[str] | None = None) -> None:
    targets = services or APP_SERVICES
    banner(f"Restarting: {', '.join(targets)} (infra stays up)")
    compose("stop", *targets)
    compose("rm", "-f", *targets)
    cmd_build(targets)
    compose_build("up", "-d", "--no-deps", *targets)


def cmd_status() -> None:
    banner("Container status")
    compose("ps", "-a")


def cmd_logs(service: str | None = None) -> None:
    args = ["logs", "-f", "--tail=200"]
    if service:
        args.append(service)
    compose(*args)


def cmd_validate() -> None:
    banner("Validate — container status + smoke suite")
    compose("ps", "-a")
    banner("Running smoke suite")
    result = subprocess.run(
        [sys.executable, str(ROOT / "scripts/smoke_local.py")],
        cwd=ROOT,
    )
    if result.returncode != 0:
        sys.exit(result.returncode)


def cmd_ci() -> None:
    """Full clean cycle: down -> build -> up -> validate -> app-down."""
    banner("CI local — full clean cycle")
    status = 0
    try:
        cmd_down()
        cmd_build()
        cmd_up()
        cmd_validate()
    except SystemExit as exc:
        status = int(exc.code) if exc.code else 1
        print(f"\n{'=' * 60}", flush=True)
        print(f"  CI FAILED (exit {status}) — dumping service logs", flush=True)
        print(f"{'=' * 60}\n", flush=True)
        compose("logs", "--tail=50", check=False)
    finally:
        banner("CI cleanup — stopping app services")
        cmd_app_down()

    if status != 0:
        sys.exit(status)
    banner("CI PASSED")


# ── entry point ────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Local stack manager",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "command",
        choices=[
            "infra-up", "infra-down",
            "app-up", "app-down",
            "up", "down",
            "build", "restart-app",
            "status", "logs", "validate", "ci",
        ],
    )
    parser.add_argument(
        "--service",
        action="append",
        dest="services",
        metavar="SERVICE",
        help=(
            "Target a specific service (repeatable). "
            "Used with: build, restart-app, logs. "
            "Example: --service risk-service --service order-service"
        ),
    )
    args = parser.parse_args()

    # --service as a single name also supported for logs (legacy)
    svc_list: list[str] | None = args.services or None
    svc_single: str | None = svc_list[0] if svc_list and len(svc_list) == 1 else None

    dispatch = {
        "infra-up":    cmd_infra_up,
        "infra-down":  cmd_infra_down,
        "app-up":      cmd_app_up,
        "app-down":    cmd_app_down,
        "up":          cmd_up,
        "down":        cmd_down,
        "build":       lambda: cmd_build(svc_list),
        "restart-app": lambda: cmd_restart_app(svc_list),
        "status":      cmd_status,
        "logs":        lambda: cmd_logs(svc_single),
        "validate":    cmd_validate,
        "ci":          cmd_ci,
    }
    dispatch[args.command]()


if __name__ == "__main__":
    main()
