"""Onboarding endpoints — direct psycopg2 DB operations (same logic as scripts/onboard.py)."""
from __future__ import annotations

import hashlib
import os
import secrets
import uuid

from typing import Optional

import psycopg2
import psycopg2.extras
from fastapi import APIRouter, HTTPException
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


class AccountUpdate(BaseModel):
    displayName: Optional[str] = None
    active: Optional[bool] = None


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


@router.delete("/accounts/{account_id}")
def delete_account(account_id: str):
    with _conn() as conn, conn.cursor() as cur:
        cur.execute("SELECT 1 FROM accounts WHERE account_id = %s", (account_id,))
        exists = cur.fetchone()
        if not exists:
            raise HTTPException(status_code=404, detail=f"Account not found: {account_id}")

        cur.execute(
            "DELETE FROM broker_accounts WHERE agent_id IN (SELECT agent_id FROM agents WHERE account_id = %s)",
            (account_id,),
        )
        cur.execute("DELETE FROM account_api_keys WHERE account_id = %s", (account_id,))
        cur.execute("DELETE FROM agents WHERE account_id = %s", (account_id,))
        cur.execute("DELETE FROM accounts WHERE account_id = %s", (account_id,))
        conn.commit()

    return {"ok": True, "deletedAccountId": account_id}


@router.put("/accounts/{account_id}")
def update_account(account_id: str, body: AccountUpdate):
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(
            """UPDATE accounts
               SET display_name = COALESCE(%s, display_name),
                   active = COALESCE(%s, active)
               WHERE account_id = %s""",
            (body.displayName, body.active, account_id),
        )
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail=f"Account not found: {account_id}")
        conn.commit()
    return {"ok": True, "accountId": account_id}


# ── Agents ────────────────────────────────────────────────────────────────────

class AgentCreate(BaseModel):
    agentId: str
    accountId: str
    displayName: str


class AgentUpdate(BaseModel):
    accountId: Optional[str] = None
    displayName: Optional[str] = None
    active: Optional[bool] = None


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


@router.put("/agents/{agent_id}")
def update_agent(agent_id: str, body: AgentUpdate):
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(
            """UPDATE agents
               SET account_id = COALESCE(%s, account_id),
                   display_name = COALESCE(%s, display_name),
                   active = COALESCE(%s, active)
               WHERE agent_id = %s""",
            (body.accountId, body.displayName, body.active, agent_id),
        )
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail=f"Agent not found: {agent_id}")
        conn.commit()
    return {"ok": True, "agentId": agent_id}


@router.delete("/agents/{agent_id}")
def delete_agent(agent_id: str):
    with _conn() as conn, conn.cursor() as cur:
        cur.execute("DELETE FROM broker_accounts WHERE agent_id = %s", (agent_id,))
        cur.execute("DELETE FROM agents WHERE agent_id = %s", (agent_id,))
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail=f"Agent not found: {agent_id}")
        conn.commit()
    return {"ok": True, "deletedAgentId": agent_id}


# ── API Keys ──────────────────────────────────────────────────────────────────

class ApiKeyGenerate(BaseModel):
    accountId: str
    generation: int = 1


class ApiKeyRevoke(BaseModel):
    keyHash: str


class ApiKeyUpdate(BaseModel):
    active: Optional[bool] = None


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


@router.put("/apikeys/{key_hash}")
def update_apikey(key_hash: str, body: ApiKeyUpdate):
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(
            "UPDATE account_api_keys SET active = COALESCE(%s, active) WHERE key_hash = %s",
            (body.active, key_hash),
        )
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail=f"API key not found: {key_hash}")
        conn.commit()
    return {"ok": True, "keyHash": key_hash}


@router.delete("/apikeys/{key_hash}")
def delete_apikey(key_hash: str):
    with _conn() as conn, conn.cursor() as cur:
        cur.execute("DELETE FROM account_api_keys WHERE key_hash = %s", (key_hash,))
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail=f"API key not found: {key_hash}")
        conn.commit()
    return {"ok": True, "deletedKeyHash": key_hash}


# ── Brokers ───────────────────────────────────────────────────────────────────

class BrokerCreate(BaseModel):
    agentId: str
    externalAccountId: str


class BrokerUpdate(BaseModel):
    externalAccountId: Optional[str] = None
    active: Optional[bool] = None


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


@router.put("/brokers/{broker_account_id}")
def update_broker(broker_account_id: str, body: BrokerUpdate):
    with _conn() as conn, conn.cursor() as cur:
        cur.execute(
            """UPDATE broker_accounts
               SET external_account_id = COALESCE(%s, external_account_id),
                   active = COALESCE(%s, active)
               WHERE broker_account_id = %s""",
            (body.externalAccountId, body.active, broker_account_id),
        )
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail=f"Broker mapping not found: {broker_account_id}")
        conn.commit()
    return {"ok": True, "brokerAccountId": broker_account_id}


@router.delete("/brokers/{broker_account_id}")
def delete_broker(broker_account_id: str):
    with _conn() as conn, conn.cursor() as cur:
        cur.execute("DELETE FROM broker_accounts WHERE broker_account_id = %s", (broker_account_id,))
        if cur.rowcount == 0:
            raise HTTPException(status_code=404, detail=f"Broker mapping not found: {broker_account_id}")
        conn.commit()
    return {"ok": True, "deletedBrokerAccountId": broker_account_id}
