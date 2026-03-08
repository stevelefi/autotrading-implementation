#!/usr/bin/env python3
"""
check.py — Pre-commit gate: runs all required local checks and reports results.

Checks run (in order):
    1. branch-check   Current branch follows GitHub flow naming convention
    2. spec-verify    Verify pinned spec baseline (tools/spec_sync.py verify)
    3. unit           Unit tests — zero failures tolerated
    4. coverage       JaCoCo coverage gate on core modules (≥50% line)
    5. e2e            E2E tests — all five test classes in tests/e2e/
    6. helm-lint      helm lint infra/helm/charts/trading-service
    7. helm-template  helm template (dry-run render)

Usage:
    python3 scripts/check.py              # full gate — all seven checks
    python3 scripts/check.py --fast       # skip e2e (checks 1-4,6,7)
    python3 scripts/check.py --skip-helm  # skip Helm checks (useful if helm not installed)
    python3 scripts/check.py --only unit coverage   # run specific checks by name

Examples:
    python3 scripts/check.py
    python3 scripts/check.py --fast
    python3 scripts/check.py --only unit e2e
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Callable

# ── configuration ──────────────────────────────────────────────────────────────

ROOT = Path(__file__).resolve().parent.parent

HELM_CHART = "infra/helm/charts/trading-service"
HELM_VALUES = f"{HELM_CHART}/values.yaml"

COVERAGE_CORE_MODULES = ",".join([
    "libs/reliability-core",
    "services/ingress-gateway-service",
    "services/risk-service",
    "services/order-service",
    "services/ibkr-connector-service",
])

TICK  = "\u2705"
CROSS = "\u274c"
SKIP  = "\u23ed\ufe0f "

# ── helpers ────────────────────────────────────────────────────────────────────


def banner(text: str) -> None:
    print(f"\n{'=' * 60}", flush=True)
    print(f"  {text}", flush=True)
    print(f"{'=' * 60}\n", flush=True)


def run(*args: str) -> int:
    cmd = list(args)
    print(f"\n$ {' '.join(cmd)}", flush=True)
    result = subprocess.run(cmd, cwd=ROOT)
    return result.returncode


# ── individual checks ──────────────────────────────────────────────────────────


def check_branch_check() -> int:
    banner("Check 1/7: branch-check")
    return run(sys.executable, "scripts/branch_check.py")


def check_spec_verify() -> int:
    banner("Check 2/7: spec-verify")
    return run(
        sys.executable, "tools/spec_sync.py", "verify",
        "--dest", "specs/vendor",
        "--version-file", "SPEC_VERSION.json",
    )


def check_unit() -> int:
    banner("Check 3/7: unit tests")
    return run("mvn", "-B", "-DskipITs=true", "test")


def check_coverage() -> int:
    banner("Check 4/7: JaCoCo coverage gate (≥50% line on core modules)")
    return run(
        "mvn", "-B", "-Pcoverage-core",
        "-pl", COVERAGE_CORE_MODULES,
        "-am", "verify",
    )


def check_e2e() -> int:
    banner("Check 5/7: e2e tests")
    return run("mvn", "-B", "-pl", "tests/e2e", "-am", "test")


def check_helm_lint() -> int:
    banner("Check 6/7: helm lint")
    if not shutil.which("helm"):
        print("[check.py] 'helm' not found on PATH — skipping helm-lint", flush=True)
        return 0
    return run("helm", "lint", HELM_CHART)


def check_helm_template() -> int:
    banner("Check 7/7: helm template (dry-run render)")
    if not shutil.which("helm"):
        print("[check.py] 'helm' not found on PATH — skipping helm-template", flush=True)
        return 0
    return run(
        "helm", "template", "trading-service", HELM_CHART,
        "-f", HELM_VALUES,
    )


# ── gate orchestration ─────────────────────────────────────────────────────────

ALL_CHECKS: list[tuple[str, Callable[[], int]]] = [
    ("branch-check",   check_branch_check),
    ("spec-verify",    check_spec_verify),
    ("unit",           check_unit),
    ("coverage",       check_coverage),
    ("e2e",            check_e2e),
    ("helm-lint",      check_helm_lint),
    ("helm-template",  check_helm_template),
]


def run_gate(checks: list[tuple[str, Callable[[], int]]]) -> None:
    results: list[tuple[str, str]] = []  # (name, status)

    for name, fn in checks:
        try:
            rc = fn()
            results.append((name, TICK + " PASS" if rc == 0 else CROSS + " FAIL"))
            if rc != 0:
                # still continue to show full summary
                pass
        except Exception as exc:  # noqa: BLE001
            print(f"\n[check.py] unexpected error in {name}: {exc}", flush=True)
            results.append((name, CROSS + " ERROR"))

    banner("Pre-commit check summary")
    any_failed = False
    for name, status in results:
        print(f"  {status:<16}  {name}", flush=True)
        if "FAIL" in status or "ERROR" in status:
            any_failed = True

    print("", flush=True)
    if any_failed:
        print("[check.py] One or more checks FAILED — fix before committing.\n", flush=True)
        sys.exit(1)
    else:
        print("[check.py] All checks PASSED.\n", flush=True)


# ── entry point ────────────────────────────────────────────────────────────────


def main() -> None:
    valid_names = [name for name, _ in ALL_CHECKS]

    parser = argparse.ArgumentParser(
        description="Pre-commit gate — run all required local checks.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--fast",
        action="store_true",
        help="Skip e2e tests (runs checks: branch-check, spec-verify, unit, coverage, helm-lint, helm-template)",
    )
    parser.add_argument(
        "--skip-helm",
        action="store_true",
        help="Skip Helm checks (helm-lint, helm-template)",
    )
    parser.add_argument(
        "--only",
        nargs="+",
        metavar="CHECK",
        choices=valid_names,
        help=f"Run only the named checks. Choices: {', '.join(valid_names)}",
    )
    args = parser.parse_args()

    checks = list(ALL_CHECKS)

    if args.only:
        checks = [(n, fn) for n, fn in ALL_CHECKS if n in args.only]
    else:
        if args.fast:
            checks = [(n, fn) for n, fn in checks if n != "e2e"]
        if args.skip_helm:
            checks = [(n, fn) for n, fn in checks if n not in ("helm-lint", "helm-template")]

    banner(f"Running {len(checks)} check(s): {', '.join(n for n, _ in checks)}")
    run_gate(checks)


if __name__ == "__main__":
    main()
