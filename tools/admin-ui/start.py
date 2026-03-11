#!/usr/bin/env python3
"""Admin UI launcher.

Usage:
    python3 tools/admin-ui/start.py           # serve pre-built frontend via FastAPI on :8765
    python3 tools/admin-ui/start.py --dev     # FastAPI :8765 + Vite dev server :5173 concurrently
    python3 tools/admin-ui/start.py --build   # npm run build, then serve on :8765
"""
from __future__ import annotations

import argparse
import subprocess
import sys
import threading
from pathlib import Path

HERE = Path(__file__).parent
BACKEND = HERE / "backend"
FRONTEND = HERE / "frontend"


def _run(cmd: list[str], cwd: Path) -> None:
    subprocess.run(cmd, cwd=cwd, check=True)


def _stream(cmd: list[str], cwd: Path) -> threading.Thread:
    def _target():
        subprocess.run(cmd, cwd=cwd)
    t = threading.Thread(target=_target, daemon=True)
    t.start()
    return t


def main() -> None:
    parser = argparse.ArgumentParser(description="Start the Autotrading Admin UI")
    parser.add_argument("--dev",   action="store_true", help="Start Vite dev server on :5173 alongside FastAPI on :8765")
    parser.add_argument("--build", action="store_true", help="Run npm run build then start FastAPI")
    args = parser.parse_args()

    if args.build:
        print("📦  Building frontend…")
        _run(["npm", "run", "build"], cwd=FRONTEND)

    if args.dev:
        print("🚀  Starting Vite dev server on http://localhost:5173  (hot reload)")
        _stream(["npm", "run", "dev"], cwd=FRONTEND)

    print("🚀  Starting FastAPI admin UI on http://localhost:8765")
    if args.dev:
        print("    (In dev mode, open http://localhost:5173 — Vite proxies /api to :8765)")
    else:
        print("    Open http://localhost:8765")
    _run(
        [
            sys.executable, "-m", "uvicorn", "main:app",
            "--host", "0.0.0.0",
            "--port", "8765",
            "--reload",
        ],
        cwd=BACKEND,
    )


if __name__ == "__main__":
    main()
