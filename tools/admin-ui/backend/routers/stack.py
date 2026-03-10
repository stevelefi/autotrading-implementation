"""Stack control endpoints — wrap scripts/stack.py as SSE streams."""
from __future__ import annotations

import asyncio
import json
import subprocess
from pathlib import Path

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

router = APIRouter()

REPO_ROOT = Path(__file__).resolve().parent.parent.parent.parent.parent
STACK_SCRIPT = REPO_ROOT / "scripts" / "stack.py"
COMPOSE_FILE = REPO_ROOT / "infra" / "local" / "docker-compose.yml"
COMPOSE_LOCAL_FILE = REPO_ROOT / "infra" / "local" / "docker-compose.local.yml"
ENV_FILE = REPO_ROOT / "infra" / "local" / ".env.compose.example"

VALID_COMMANDS = {
    "infra-up", "app-up", "up", "down", "infra-down", "app-down",
    "build", "restart-app", "status", "logs", "validate", "ci",
}


class CommandRequest(BaseModel):
    services: list[str] = []


async def _stream_subprocess(cmd: list[str]):
    """Run a subprocess and yield SSE data lines."""
    proc = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
        cwd=str(REPO_ROOT),
    )
    assert proc.stdout is not None
    async for raw in proc.stdout:
        line = raw.decode("utf-8", errors="replace").rstrip()
        yield f"data: {json.dumps({'line': line})}\n\n"
    code = await proc.wait()
    yield f"data: {json.dumps({'done': True, 'exitCode': code})}\n\n"


@router.post("/{command}")
async def run_stack_command(command: str, body: CommandRequest = CommandRequest()):
    if command not in VALID_COMMANDS:
        raise HTTPException(status_code=400, detail=f"Unknown command: {command}")

    cmd = ["python3", str(STACK_SCRIPT), command]
    for svc in body.services:
        cmd += ["--service", svc]

    return StreamingResponse(
        _stream_subprocess(cmd),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.get("/status")
def get_status():
    """Return docker compose ps output as structured JSON."""
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
