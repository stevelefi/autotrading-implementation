#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VENV_DIR="$ROOT_DIR/.venv"
PYTHON_BIN="$VENV_DIR/bin/python"
PIP_BIN="$VENV_DIR/bin/pip"
REQ_FILE="$ROOT_DIR/tools/admin-ui/backend/requirements.txt"
START_SCRIPT="$ROOT_DIR/tools/admin-ui/start.py"

MODE="${1:-start}"

print_help() {
  cat <<'EOF'
Usage:
  ./scripts/admin-ui.sh [start|dev|build|install|help]

Modes:
  start   Start FastAPI UI on :8765 (default)
  dev     Start FastAPI :8765 + Vite :5173
  build   Build frontend, then start FastAPI
  install Create .venv (if needed) and install backend requirements only
EOF
}

ensure_venv() {
  if [[ ! -x "$PYTHON_BIN" ]]; then
    python3 -m venv "$VENV_DIR"
  fi
}

ensure_requirements() {
  if ! "$PYTHON_BIN" -c "import fastapi, uvicorn" >/dev/null 2>&1; then
    "$PIP_BIN" install -r "$REQ_FILE"
  fi
}

run_start() {
  local args=()
  if [[ "$MODE" == "dev" ]]; then
    args+=("--dev")
  elif [[ "$MODE" == "build" ]]; then
    args+=("--build")
  fi

  exec "$PYTHON_BIN" "$START_SCRIPT" "${args[@]}"
}

case "$MODE" in
  start|dev|build|install)
    ;;
  help|-h|--help)
    print_help
    exit 0
    ;;
  *)
    echo "Unknown mode: $MODE" >&2
    print_help
    exit 1
    ;;
esac

ensure_venv
ensure_requirements

if [[ "$MODE" == "install" ]]; then
  echo "Admin UI environment is ready: $PYTHON_BIN"
  exit 0
fi

run_start
