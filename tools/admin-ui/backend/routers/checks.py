"""Pre-commit gate endpoint — wraps scripts/check.py as SSE stream."""
from __future__ import annotations

import asyncio
import json
import re
from pathlib import Path

from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

router = APIRouter()

REPO_ROOT = Path(__file__).resolve().parent.parent.parent.parent.parent
CHECK_SCRIPT = REPO_ROOT / "scripts" / "check.py"

VALID_CHECKS = {
    "branch-check", "spec-verify", "agent-sync",
    "unit", "coverage", "e2e", "helm-lint", "helm-template",
}

# Matches lines like "✅ PASS   unit" or "❌ FAIL   e2e"
SUMMARY_RE = re.compile(r"(✅|❌)\s+(PASS|FAIL)\s+(\S+)")


class CheckRequest(BaseModel):
    fast: bool = False
    skipHelm: bool = False
    only: list[str] = []


async def _stream_subprocess(cmd: list[str]):
    proc = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
        cwd=str(REPO_ROOT),
    )
    assert proc.stdout is not None
    summary: list[dict] = []
    async for raw in proc.stdout:
        line = raw.decode("utf-8", errors="replace").rstrip()
        yield f"data: {json.dumps({'line': line})}\n\n"
        m = SUMMARY_RE.search(line)
        if m:
            summary.append({"check": m.group(3), "passed": m.group(2) == "PASS"})
    code = await proc.wait()
    yield f"data: {json.dumps({'done': True, 'exitCode': code, 'summary': summary})}\n\n"


@router.post("/run")
async def run_checks(body: CheckRequest = CheckRequest()):
    cmd = ["python3", str(CHECK_SCRIPT)]
    if body.fast:
        cmd.append("--fast")
    if body.skipHelm:
        cmd.append("--skip-helm")
    for check in body.only:
        if check in VALID_CHECKS:
            cmd += ["--only", check]

    return StreamingResponse(
        _stream_subprocess(cmd),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
