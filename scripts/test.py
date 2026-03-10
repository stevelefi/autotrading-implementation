#!/usr/bin/env python3
"""
test.py — Master test runner for the autotrading monorepo.

Maven-based commands (no live stack required):
    unit        Run all unit tests
    coverage    JaCoCo coverage gate on core modules (≥50% line)
    e2e         All five test classes in tests/e2e/
    all         unit + coverage + e2e  (default fail-fast)

Live-stack commands (requires: python3 scripts/stack.py up):
    smoke       6-phase live integration smoke suite
    load        20-order concurrent load test + system report
    manual      Single end-to-end event trace (pass extra args after --)

Combined:
    full        unit + coverage + e2e + smoke  (complete CI equivalent)

Options:
    --module <path>     Target one Maven module (unit/coverage only)
    --no-fail-fast      Keep running after a failure (all/full only)

Examples:
    python3 scripts/test.py unit
    python3 scripts/test.py unit --module services/risk-service
    python3 scripts/test.py coverage
    python3 scripts/test.py e2e
    python3 scripts/test.py all
    python3 scripts/test.py all --no-fail-fast

    # requires live stack (python3 scripts/stack.py up first)
    python3 scripts/test.py smoke
    python3 scripts/test.py load
    python3 scripts/test.py manual
    python3 scripts/test.py manual -- --agent-id agent-smoke --qty 5 --side SELL
    python3 scripts/test.py manual -- --skip-loki --no-browser

    # full CI run (stack must be up for the smoke phase)
    python3 scripts/test.py full
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


def run_script(script: str, *extra_args: str, check: bool = True) -> int:
    """Run a sibling script via the same Python interpreter."""
    cmd = [sys.executable, str(ROOT / "scripts" / script)] + list(extra_args)
    print(f"\n$ {' '.join(cmd)}", flush=True)
    result = subprocess.run(cmd, cwd=ROOT)
    if check and result.returncode != 0:
        print(f"\n[test.py] FAILED (exit {result.returncode}): {script}", flush=True)
        sys.exit(result.returncode)
    return result.returncode


# ── Maven commands ─────────────────────────────────────────────────────────────


def cmd_unit(module: str | None = None) -> None:
    banner("Unit tests")
    # Reinstall the contracts module before running tests to ensure the
    # protobuf-generated classes in ~/.m2 match the reactor's compiled output.
    # Without this, a prior clean resets target/ but leaves a stale snapshot
    # in the local repo, causing NoSuchMethodError on synthetic accessor
    # methods (e.g. CancelOrderRequest.access$N) in ibkr-connector tests.
    mvn("-B", "-DskipTests", "install", "-pl", "libs/contracts")
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


# ── Live-stack commands ────────────────────────────────────────────────────────


def cmd_smoke() -> None:
    banner("Smoke suite (6-phase live integration — requires stack up)")
    print("  Tip: python3 scripts/stack.py up   (if stack is not running)\n", flush=True)
    run_script("smoke_local.py")


def cmd_load() -> None:
    banner("Load test — 20 concurrent orders + system report (requires stack up)")
    print("  Tip: python3 scripts/stack.py up   (if stack is not running)\n", flush=True)
    run_script("load_20_orders.py")


def cmd_manual(extra_args: list[str]) -> None:
    banner("Manual trace — single end-to-end event (requires stack up)")
    print("  Tip: python3 scripts/stack.py up   (if stack is not running)", flush=True)
    if extra_args:
        print(f"  Forwarding args: {' '.join(extra_args)}\n", flush=True)
    else:
        print("  Running with defaults (agent-smoke-pipeline, BUY qty=1).\n", flush=True)
        print("  Pass extra args after '--', e.g.:\n"
              "    python3 scripts/test.py manual -- --agent-id my-agent --qty 5 --side SELL\n"
              "    python3 scripts/test.py manual -- --skip-loki --no-browser\n",
              flush=True)
    run_script("manual_trace.py", *extra_args)


# ── Composite commands ─────────────────────────────────────────────────────────


def cmd_all(fail_fast: bool = True, module: str | None = None) -> None:
    banner("Maven test suite: unit + coverage + e2e")
    steps: list[tuple[str, object]] = [
        ("unit",     lambda: cmd_unit(module)),
        ("coverage", lambda: cmd_coverage(module)),
        ("e2e",      cmd_e2e),
    ]
    _run_steps(steps, fail_fast)


def cmd_full(fail_fast: bool = True, module: str | None = None) -> None:
    banner("Full CI test suite: unit + coverage + e2e + smoke")
    print("  NOTE: smoke phase requires the live stack to be running.\n"
          "        Run 'python3 scripts/stack.py up' before this command.\n",
          flush=True)
    steps: list[tuple[str, object]] = [
        ("unit",     lambda: cmd_unit(module)),
        ("coverage", lambda: cmd_coverage(module)),
        ("e2e",      cmd_e2e),
        ("smoke",    cmd_smoke),
    ]
    _run_steps(steps, fail_fast)


def _run_steps(steps: list[tuple[str, object]], fail_fast: bool) -> None:
    failed: list[str] = []
    for name, fn in steps:
        try:
            fn()  # type: ignore[operator]
        except SystemExit as exc:
            if fail_fast:
                raise
            failed.append(name)
            print(f"\n[test.py] {name} FAILED (exit {exc.code}) — continuing\n", flush=True)

    if failed:
        banner(f"FAILED steps: {', '.join(failed)}")
        sys.exit(1)
    else:
        banner("All steps PASSED")


# ── entry point ────────────────────────────────────────────────────────────────


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Master test runner for the autotrading monorepo.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "command",
        choices=["unit", "coverage", "e2e", "smoke", "load", "manual", "all", "full"],
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
        help="Continue running remaining steps after a failure (all/full only)",
    )
    # Extra args forwarded verbatim to manual_trace.py when command=manual.
    # Everything after '--' lands here automatically via argparse.REMAINDER.
    parser.add_argument(
        "extra",
        nargs=argparse.REMAINDER,
        help="Extra arguments forwarded to manual_trace.py (command=manual only). "
             "Separate with '--', e.g.:  test.py manual -- --agent-id foo --side SELL",
    )
    args = parser.parse_args()

    # Strip leading '--' separator that argparse.REMAINDER preserves
    extra = [a for a in (args.extra or []) if a != "--"]

    fail_fast = not args.no_fail_fast

    dispatch = {
        "unit":     lambda: cmd_unit(args.module),
        "coverage": lambda: cmd_coverage(args.module),
        "e2e":      cmd_e2e,
        "smoke":    cmd_smoke,
        "load":     cmd_load,
        "manual":   lambda: cmd_manual(extra),
        "all":      lambda: cmd_all(fail_fast, args.module),
        "full":     lambda: cmd_full(fail_fast, args.module),
    }
    dispatch[args.command]()


if __name__ == "__main__":
    main()
