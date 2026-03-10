"""Onboarding endpoints — direct psycopg2 DB operations (same logic as scripts/onboard.py)."""
from __future__ import annotations

import hashlib
import os
import secrets
import uuid

from typing import Optional

import psycopg2
import psycopg2.extras
from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter()

# DB connection defaults mirror onboard.py
_DEFAULTS = {
    "host":     "localhost",
    "port":     5432,
    "dbname":   os.environ.get("POSTGRES_DB",       "autotrading"),
    "user":     os.environ.get("POSTGRES_USER",     "autotrading"),
    "password": os.environ.get("POSTGRES_PASSWORD", "autotrading_dev_only"),
}


def _conn():
    url = os.environ.get("DATABASE_URL")
    if url:
        return psycopg2.connect(url, cursor_factory=psycopg2.extras.RealDictCursor)
    return psycopg2.connect(**_DEFAULTS, cursor_factory=psycopg2.extras.RealDictCursor)


def _sha256(raw: str) -> str:
    return hashlib.sha256(raw.encode()).hexdigest()


# ── Accounts ──────────────────────────────────────────────────────────────────

class AccountCreate(BaseModel):
    accountId: str
    displayName: str


@router.get("/accounts")
def list_accounts():
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT account_id, display_name, active, created_at FROM accounts ORDER BY created_at DESC"
        )
        return {"accounts": [dict(r) for r in cur.fetchall()]}


@router.post("/accounts")
def create_account(body: AccountCreate):
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO accounts (account_id, display_name) VALUES (%s, %s) ON CONFLICT DO NOTHING",
            (body.accountId, body.displayName),
        )
        conn.commit()
    return {"ok": True}


# ── Agents ────────────────────────────────────────────────────────────────────

class AgentCreate(BaseModel):
    agentId: str
    accountId: str
    displayName: str


@router.get("/agents")
def list_agents(accountId: Optional[str] = None):
    with _conn() as conn, conn.cursor() as cur:
        if accountId:
            cur.execute(
                "SELECT agent_id, account_id, display_name, active, created_at FROM agents WHERE account_id = %s ORDER BY created_at DESC",
                (accountId,),
            )
        else:
            cur.execute(
                "SELECT agent_id, account_id, display_name, active, created_at FROM agents ORDER BY created_at DESC"
            )
        return {"agents": [dict(r) for r in cur.fetchall()]}


@router.post("/agents")
def create_agent(body: AgentCreate):
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO agents (agent_id, account_id, display_name) VALUES (%s, %s, %s) ON CONFLICT DO NOTHING",
            (body.agentId, body.accountId, body.displayName),
        )
        conn.commit()
    return {"ok": True}


# ── API Keys ──────────────────────────────────────────────────────────────────

class ApiKeyGenerate(BaseModel):
    accountId: str
    generation: int = 1


class ApiKeyRevoke(BaseModel):
    keyHash: str


@router.get("/apikeys")
def list_apikeys(accountId: str):
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT key_hash, generation, active, expires_at, created_at FROM account_api_keys WHERE account_id = %s ORDER BY created_at DESC",
            (accountId,),
        )
        return {"apikeys": [dict(r) for r in cur.fetchall()]}


@router.post("/apikeys/generate")
def generate_apikey(body: ApiKeyGenerate):
    raw = secrets.token_urlsafe(32)
    sha = _sha256(raw)
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO account_api_keys (key_hash, account_id, generation) VALUES (%s, %s, %s) ON CONFLICT DO NOTHING",
            (sha, body.accountId, body.generation),
        )
        conn.commit()
    return {
        "rawKey":      raw,
        "sha256":      sha,
        "headerValue": f"Bearer {raw}",
    }


@router.post("/apikeys/revoke")
def revoke_apikey(body: ApiKeyRevoke):
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(
            "UPDATE account_api_keys SET active = FALSE WHERE key_hash = %s",
            (body.keyHash,),
        )
        conn.commit()
    return {"ok": True}


# ── Brokers ───────────────────────────────────────────────────────────────────

class BrokerCreate(BaseModel):
    agentId: str
    externalAccountId: str


@router.get("/brokers")
def list_brokers(agentId: Optional[str] = None):
    with _conn() as conn, conn.cursor() as cur:
        if agentId:
            cur.execute(
                "SELECT broker_account_id, agent_id, broker_id, external_account_id, active, created_at FROM broker_accounts WHERE agent_id = %s ORDER BY created_at DESC",
                (agentId,),
            )
        else:
            cur.execute(
                "SELECT broker_account_id, agent_id, broker_id, external_account_id, active, created_at FROM broker_accounts ORDER BY created_at DESC"
            )
        return {"brokers": [dict(r) for r in cur.fetchall()]}


@router.post("/brokers")
def create_broker(body: BrokerCreate):
    broker_account_id = f"ba-{uuid.uuid4()}"
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(
            """INSERT INTO broker_accounts (broker_account_id, agent_id, broker_id, external_account_id)
               VALUES (%s, %s, 'ibkr', %s)
               ON CONFLICT (agent_id, broker_id) DO UPDATE SET external_account_id = EXCLUDED.external_account_id""",
            (broker_account_id, body.agentId, body.externalAccountId),
        )
        conn.commit()
    return {"ok": True}
