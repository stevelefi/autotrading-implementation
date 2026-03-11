"""Trace viewer endpoint — wraps scripts/trace.py --json."""
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
TRACE_SCRIPT = REPO_ROOT / "scripts" / "trace.py"
MANUAL_TRACE_SCRIPT = REPO_ROOT / "scripts" / "manual_trace.py"


class TraceQuery(BaseModel):
    traceId: Optional[str] = None
    clientEventId: Optional[str] = None
    agentId: Optional[str] = None
    orderIntentId: Optional[str] = None
    signalId: Optional[str] = None
    service: Optional[str] = None
    level: Optional[str] = None
    since: str = "1h"


class ManualTraceRequest(BaseModel):
    clientEventId: Optional[str] = None
    agentId: Optional[str] = None
    side: Optional[str] = None
    qty: Optional[int] = None
    token: Optional[str] = None
    lokiSince: str = "15m"


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


@router.post("/manual")
async def run_manual_trace(body: ManualTraceRequest):
    cmd = ["python3", str(MANUAL_TRACE_SCRIPT), "--no-browser", "--loki-since", body.lokiSince]
    if body.clientEventId:
        cmd += ["--client-event-id", body.clientEventId]
    if body.agentId:
        cmd += ["--agent-id", body.agentId]
    if body.side:
        cmd += ["--side", body.side]
    if body.qty is not None:
        cmd += ["--qty", str(body.qty)]
    if body.token:
        cmd += ["--token", body.token]

    return StreamingResponse(
        _stream_subprocess(cmd),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
