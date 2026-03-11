#!/usr/bin/env python3
"""Admin UI bootstrap launcher.

Usage:
  python3 scripts/admin-ui.py [start|dev|build|install|help]
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
VENV_DIR = ROOT_DIR / ".venv"
PYTHON_BIN = VENV_DIR / "bin" / "python"
PIP_BIN = VENV_DIR / "bin" / "pip"
REQ_FILE = ROOT_DIR / "tools" / "admin-ui" / "backend" / "requirements.txt"
START_SCRIPT = ROOT_DIR / "tools" / "admin-ui" / "start.py"


def print_help() -> None:
    print(
        "Usage:\n"
        "  python3 scripts/admin-ui.py [start|dev|build|install|help]\n\n"
        "Modes:\n"
        "  start   Start FastAPI UI on :8765 (default)\n"
        "  dev     Start FastAPI :8765 + Vite :5173\n"
        "  build   Build frontend, then start FastAPI\n"
        "  install Create .venv (if needed) and install backend requirements only"
    )


def ensure_venv() -> None:
    if not PYTHON_BIN.exists():
        subprocess.run([sys.executable, "-m", "venv", str(VENV_DIR)], check=True, cwd=str(ROOT_DIR))


def ensure_requirements() -> None:
    probe = subprocess.run(
        [str(PYTHON_BIN), "-c", "import fastapi, uvicorn"],
        cwd=str(ROOT_DIR),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    if probe.returncode != 0:
        subprocess.run([str(PIP_BIN), "install", "-r", str(REQ_FILE)], check=True, cwd=str(ROOT_DIR))


def run_start(mode: str) -> int:
    args: list[str] = []
    if mode == "dev":
        args.append("--dev")
    elif mode == "build":
        args.append("--build")

    return subprocess.call([str(PYTHON_BIN), str(START_SCRIPT), *args], cwd=str(ROOT_DIR))


def main() -> int:
    mode = sys.argv[1] if len(sys.argv) > 1 else "start"

    if mode in {"help", "-h", "--help"}:
        print_help()
        return 0

    if mode not in {"start", "dev", "build", "install"}:
        print(f"Unknown mode: {mode}", file=sys.stderr)
        print_help()
        return 1

    ensure_venv()
    ensure_requirements()

    if mode == "install":
        print(f"Admin UI environment is ready: {PYTHON_BIN}")
        return 0

    return run_start(mode)


if __name__ == "__main__":
    raise SystemExit(main())
