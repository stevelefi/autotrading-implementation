"""Test runner endpoints — wrap scripts/test.py as SSE streams."""
from __future__ import annotations

import asyncio
import json
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

router = APIRouter()

REPO_ROOT = Path(__file__).resolve().parent.parent.parent.parent.parent
TEST_SCRIPT = REPO_ROOT / "scripts" / "test.py"

VALID_COMMANDS = {"unit", "coverage", "e2e", "smoke", "load", "all", "full"}


class TestRequest(BaseModel):
    module: Optional[str] = None


async def _stream_subprocess(cmd: list[str]):
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
async def run_test(command: str, body: TestRequest = TestRequest()):
    if command not in VALID_COMMANDS:
        raise HTTPException(status_code=400, detail=f"Unknown command: {command}")

    cmd = ["python3", str(TEST_SCRIPT), command]
    if body.module:
        cmd += ["--module", body.module]

    return StreamingResponse(
        _stream_subprocess(cmd),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
