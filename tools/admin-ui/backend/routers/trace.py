"""Trace viewer endpoint — wraps scripts/trace.py --json."""
from __future__ import annotations

import asyncio
import json
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

router = APIRouter()

REPO_ROOT = Path(__file__).resolve().parent.parent.parent.parent.parent
TRACE_SCRIPT = REPO_ROOT / "scripts" / "trace.py"


class TraceQuery(BaseModel):
    traceId: Optional[str] = None
    clientEventId: Optional[str] = None
    agentId: Optional[str] = None
    orderIntentId: Optional[str] = None
    signalId: Optional[str] = None
    service: Optional[str] = None
    level: Optional[str] = None
    since: str = "1h"


@router.post("/query")
async def query_trace(body: TraceQuery):
    cmd = ["python3", str(TRACE_SCRIPT), "--json", "--since", body.since]
    if body.traceId:
        cmd += ["--trace-id", body.traceId]
    if body.clientEventId:
        cmd += ["--client-event-id", body.clientEventId]
    if body.agentId:
        cmd += ["--agent-id", body.agentId]
    if body.orderIntentId:
        cmd += ["--order-intent-id", body.orderIntentId]
    if body.signalId:
        cmd += ["--signal-id", body.signalId]
    if body.service:
        cmd += ["--service", body.service]
    if body.level:
        cmd += ["--level", body.level]

    proc = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
        cwd=str(REPO_ROOT),
    )
    stdout, stderr = await proc.communicate()
    if proc.returncode != 0:
        raise HTTPException(status_code=502, detail=stderr.decode(errors="replace"))
    try:
        return {"entries": json.loads(stdout.decode())}
    except json.JSONDecodeError:
        return {"entries": []}
