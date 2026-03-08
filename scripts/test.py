#!/usr/bin/env python3
"""
test.py — Run unit, coverage, and e2e tests for the autotrading monorepo.

Usage:
    python3 scripts/test.py <command> [options]

Commands:
    unit        Run all unit tests (mvn -B -DskipITs=true test)
    coverage    Run JaCoCo coverage gate on core modules (minimum 50% line coverage)
    e2e         Run all five e2e test classes in tests/e2e/
    all         Run unit + coverage + e2e in sequence (fails fast)

Options:
    --module <path>   Target a single Maven module, e.g. services/risk-service
                      Only applies to: unit, coverage
    --no-fail-fast    Continue on first failure (default: fail fast)

Examples:
    python3 scripts/test.py unit
    python3 scripts/test.py unit --module services/risk-service
    python3 scripts/test.py coverage
    python3 scripts/test.py e2e
    python3 scripts/test.py all
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

# ── configuration ──────────────────────────────────────────────────────────────

ROOT = Path(__file__).resolve().parent.parent

COVERAGE_CORE_MODULES = ",".join([
    "libs/reliability-core",
    "services/ingress-gateway-service",
    "services/risk-service",
    "services/order-service",
    "services/ibkr-connector-service",
])

# ── helpers ────────────────────────────────────────────────────────────────────


def banner(text: str) -> None:
    print(f"\n{'=' * 60}", flush=True)
    print(f"  {text}", flush=True)
    print(f"{'=' * 60}\n", flush=True)


def mvn(*args: str, check: bool = True) -> int:
    cmd = ["mvn"] + list(args)
    print(f"\n$ {' '.join(cmd)}", flush=True)
    result = subprocess.run(cmd, cwd=ROOT)
    if check and result.returncode != 0:
        print(f"\n[test.py] FAILED (exit {result.returncode}): {' '.join(cmd)}", flush=True)
        sys.exit(result.returncode)
    return result.returncode


# ── commands ───────────────────────────────────────────────────────────────────


def cmd_unit(module: str | None = None) -> None:
    banner("Unit tests")
    if module:
        mvn("-B", "-DskipITs=true", "-pl", module, "-am", "test")
    else:
        mvn("-B", "-DskipITs=true", "test")


def cmd_coverage(module: str | None = None) -> None:
    banner("JaCoCo coverage gate (≥50% line coverage on core modules)")
    if module:
        mvn("-B", "-Pcoverage-core", "-pl", module, "-am", "verify")
    else:
        mvn("-B", "-Pcoverage-core", "-pl", COVERAGE_CORE_MODULES, "-am", "verify")


def cmd_e2e() -> None:
    banner("E2E tests (tests/e2e — all five test classes)")
    mvn("-B", "-pl", "tests/e2e", "-am", "test")


def cmd_all(fail_fast: bool = True) -> None:
    banner("Full test suite: unit + coverage + e2e")
    steps = [
        ("unit",     cmd_unit),
        ("coverage", cmd_coverage),
        ("e2e",      cmd_e2e),
    ]
    failed: list[str] = []
    for name, fn in steps:
        try:
            fn()
        except SystemExit as exc:
            if fail_fast:
                raise
            failed.append(name)
            print(f"\n[test.py] {name} FAILED (exit {exc.code}) — continuing\n", flush=True)

    if failed:
        banner(f"FAILED steps: {', '.join(failed)}")
        sys.exit(1)
    else:
        banner("All tests PASSED")


# ── entry point ────────────────────────────────────────────────────────────────


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Run tests for the autotrading monorepo.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "command",
        choices=["unit", "coverage", "e2e", "all"],
        help="Test suite to run",
    )
    parser.add_argument(
        "--module",
        metavar="PATH",
        help="Target a single Maven module (unit/coverage only), e.g. services/risk-service",
    )
    parser.add_argument(
        "--no-fail-fast",
        action="store_true",
        help="Continue running remaining steps after a failure (all command only)",
    )
    args = parser.parse_args()

    dispatch = {
        "unit":     lambda: cmd_unit(args.module),
        "coverage": lambda: cmd_coverage(args.module),
        "e2e":      cmd_e2e,
        "all":      lambda: cmd_all(fail_fast=not args.no_fail_fast),
    }
    dispatch[args.command]()


if __name__ == "__main__":
    main()
