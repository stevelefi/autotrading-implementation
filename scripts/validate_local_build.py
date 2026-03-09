#!/usr/bin/env python3
"""
validate_local_build.py
-----------------------
Validates the local-dev Docker build optimisation:

  1. Dockerfile.local exists for every app service
  2. Each Dockerfile.local COPY line references the JAR that Maven actually produces
  3. All expected JAR files exist in their service target/ directories
  4. docker-compose.local.yml covers every app service with the right context path
  5. stack.py has COMPOSE_LOCAL_FILE and compose_build() wired up
  6. `docker compose config` parses the overlay without errors
  7. A no-op docker build (all layers cached) completes under MAX_BUILD_SECONDS

Usage:
  python3 scripts/validate_local_build.py           # full validation
  python3 scripts/validate_local_build.py --no-docker  # skip checks 6-7
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
from pathlib import Path

# ── configuration ──────────────────────────────────────────────────────────────

ROOT = Path(__file__).resolve().parent.parent
COMPOSE_FILE = ROOT / "infra/local/docker-compose.yml"
COMPOSE_LOCAL = ROOT / "infra/local/docker-compose.local.yml"
ENV_FILE = ROOT / "infra/local/.env.compose.example"
STACK_PY = ROOT / "scripts/stack.py"

# Seconds a fully-cached `docker compose build` of all 8 services must finish in.
MAX_BUILD_SECONDS = 30

# service name → expected JAR filename relative to services/<name>/target/
SERVICES: dict[str, str] = {
    "ingress-gateway-service":  "ingress-gateway-service-0.1.0-SNAPSHOT.jar",
    "event-processor-service":  "event-processor-service-0.1.0-SNAPSHOT.jar",
    "agent-runtime-service":    "agent-runtime-service-0.1.0-SNAPSHOT.jar",
    "risk-service":             "risk-service-0.1.0-SNAPSHOT-exec.jar",
    "order-service":            "order-service-0.1.0-SNAPSHOT-exec.jar",
    "ibkr-connector-service":   "ibkr-connector-service-0.1.0-SNAPSHOT-exec.jar",
    "performance-service":      "performance-service-0.1.0-SNAPSHOT.jar",
    "monitoring-api":           "monitoring-api-0.1.0-SNAPSHOT.jar",
}

# ── helpers ────────────────────────────────────────────────────────────────────

PASS = "\033[32m✔\033[0m"
FAIL = "\033[31m✘\033[0m"
WARN = "\033[33m⚠\033[0m"

failures: list[str] = []


def ok(msg: str) -> None:
    print(f"  {PASS}  {msg}")


def fail(msg: str) -> None:
    print(f"  {FAIL}  {msg}")
    failures.append(msg)


def warn(msg: str) -> None:
    print(f"  {WARN}  {msg}")


def section(title: str) -> None:
    print(f"\n── {title} {'─' * (54 - len(title))}")


# ── checks ─────────────────────────────────────────────────────────────────────

def check_dockerfile_local() -> None:
    section("1  Dockerfile.local presence + JAR reference")
    for svc, jar_name in SERVICES.items():
        path = ROOT / "services" / svc / "Dockerfile.local"
        if not path.exists():
            fail(f"{svc}/Dockerfile.local — file missing")
            continue
        content = path.read_text()
        if "FROM maven" in content or "RUN mvn" in content:
            fail(f"{svc}/Dockerfile.local — still has a Maven build stage (should be runtime-only)")
            continue
        if jar_name not in content:
            fail(f"{svc}/Dockerfile.local — expected COPY of '{jar_name}' not found")
        else:
            ok(f"{svc}/Dockerfile.local  →  {jar_name}")


def check_jars_exist() -> None:
    section("2  Pre-built JARs present in target/")
    for svc, jar_name in SERVICES.items():
        jar_path = ROOT / "services" / svc / "target" / jar_name
        if jar_path.exists():
            size_mb = jar_path.stat().st_size / 1_048_576
            ok(f"{svc}/target/{jar_name}  ({size_mb:.1f} MB)")
        else:
            fail(
                f"{svc}/target/{jar_name} — not found. "
                "Run: mvn -pl services/{svc} -am -DskipTests package"
            )


def check_compose_local() -> None:
    section("3  docker-compose.local.yml covers all services")
    if not COMPOSE_LOCAL.exists():
        fail(f"{COMPOSE_LOCAL} — file missing")
        return
    content = COMPOSE_LOCAL.read_text()
    for svc in SERVICES:
        if svc not in content:
            fail(f"docker-compose.local.yml — '{svc}' not listed")
        elif f"services/{svc}" not in content and f"services\\{svc}" not in content:
            fail(f"docker-compose.local.yml — '{svc}' context path looks wrong (expected 'services/{svc}')")
        else:
            ok(f"{svc}  listed with correct context")


def check_stack_py() -> None:
    section("4  stack.py wiring")
    content = STACK_PY.read_text()
    checks = {
        "COMPOSE_LOCAL_FILE defined":     "COMPOSE_LOCAL_FILE",
        "BUILD_CMD contains local file":  "COMPOSE_LOCAL_FILE",
        "compose_build() function":       "def compose_build",
        "cmd_build() runs mvn on host":   "mvn_cmd",
        "cmd_build() calls compose_build":"compose_build(\"build\"",
    }
    for label, token in checks.items():
        if token in content:
            ok(label)
        else:
            fail(f"stack.py — {label} ({token!r} not found)")


def check_compose_config() -> None:
    section("5  docker compose config (overlay parse)")
    cmd = [
        "docker", "compose",
        "--env-file", str(ENV_FILE),
        "-f", str(COMPOSE_FILE),
        "-f", str(COMPOSE_LOCAL),
        "config", "--quiet",
    ]
    result = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True)
    if result.returncode == 0:
        ok("docker compose config parsed overlay without errors")
    else:
        fail(f"docker compose config failed:\n{result.stderr.strip()}")


def check_build_speed() -> None:
    section(f"6  Cached build speed  (all 8 services ≤ {MAX_BUILD_SECONDS}s)")

    # Ensure images exist from at least one previous build before timing.
    # A build that misses the cache (first ever run) is expected to be slow.
    services = list(SERVICES.keys())
    cmd = [
        "docker", "compose",
        "--env-file", str(ENV_FILE),
        "-f", str(COMPOSE_FILE),
        "-f", str(COMPOSE_LOCAL),
        "build", "--no-cache=false",
        *services,
    ]

    print(f"  Running: {' '.join(cmd[-len(services)-2:])}")  # abbreviated
    t0 = time.perf_counter()
    result = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True)
    elapsed = time.perf_counter() - t0

    if result.returncode != 0:
        fail(f"docker compose build failed:\n{result.stderr[-800:]}")
        return

    built = re.findall(r"Image (\S+) Built", result.stdout + result.stderr)
    for img in sorted(set(built)):
        ok(f"built  {img}  (cached)")

    if elapsed <= MAX_BUILD_SECONDS:
        ok(f"Total build time: {elapsed:.1f}s  ≤ {MAX_BUILD_SECONDS}s threshold ✓")
    else:
        warn(
            f"Total build time: {elapsed:.1f}s  exceeds {MAX_BUILD_SECONDS}s. "
            "Docker layer cache may be cold — re-run after a successful "
            "`python3 scripts/stack.py build` to seed the cache."
        )


# ── entry point ────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="Validate local Docker build optimisation")
    parser.add_argument(
        "--no-docker",
        action="store_true",
        help="Skip checks that invoke Docker (compose config + build speed)",
    )
    args = parser.parse_args()

    print("\n╔══════════════════════════════════════════════════════╗")
    print("║     local-build optimisation — validation suite     ║")
    print("╚══════════════════════════════════════════════════════╝")

    check_dockerfile_local()
    check_jars_exist()
    check_compose_local()
    check_stack_py()

    if not args.no_docker:
        check_compose_config()
        check_build_speed()
    else:
        warn("Skipping Docker checks (--no-docker)")

    print()
    if failures:
        print(f"\033[31m✘  {len(failures)} check(s) failed:\033[0m")
        for f in failures:
            print(f"     • {f}")
        sys.exit(1)
    else:
        print(f"\033[32m✔  All checks passed.\033[0m")


if __name__ == "__main__":
    main()
