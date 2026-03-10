"""FastAPI admin UI backend — wraps scripts/*.py as SSE endpoints."""
from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from routers import stack, tests, checks, health, onboard, trace

app = FastAPI(title="Autotrading Admin UI", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(stack.router,   prefix="/api/stack")
app.include_router(tests.router,   prefix="/api/tests")
app.include_router(checks.router,  prefix="/api/checks")
app.include_router(health.router,  prefix="/api/health")
app.include_router(onboard.router, prefix="/api/onboard")
app.include_router(trace.router,   prefix="/api/trace")

# Serve built frontend in production
FRONTEND_DIST = Path(__file__).parent.parent / "frontend" / "dist"
if FRONTEND_DIST.exists():
    app.mount("/", StaticFiles(directory=str(FRONTEND_DIST), html=True), name="static")
