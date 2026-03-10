#!/usr/bin/env python3
"""
onboard.py — Account / Agent / API-key / Broker-account management CLI.

Creates and lists the V10 auth/routing records directly in the local
Postgres database (running in Docker).

Usage
-----
  # Accounts
  python3 scripts/onboard.py account create  <account-id> <display-name>
  python3 scripts/onboard.py account list

  # Agents
  python3 scripts/onboard.py agent create  <agent-id> <account-id> <display-name>
  python3 scripts/onboard.py agent list    [<account-id>]

  # API keys
  python3 scripts/onboard.py apikey generate  <account-id>          # random key, prints raw value
  python3 scripts/onboard.py apikey create    <account-id> <raw-key> [<generation>]
  python3 scripts/onboard.py apikey list      <account-id>
  python3 scripts/onboard.py apikey revoke    <key-hash>

  # Broker accounts
  python3 scripts/onboard.py broker create  <agent-id> <external-account-id>
  python3 scripts/onboard.py broker list    [<agent-id>]

Database connection
-------------------
  Primary   : DATABASE_URL env var  (postgresql://user:pass@host:port/db)
  Fallback  : docker exec into the postgres container
  Container : POSTGRES_CONTAINER env var   (default: autotrading-local-postgres-1)
  Credentials: POSTGRES_USER / POSTGRES_PASSWORD / POSTGRES_DB  (defaults from .env.compose.example)
"""
from __future__ import annotations

import hashlib
import json
import os
import secrets
import subprocess
import sys
import textwrap
from datetime import datetime, timezone
from typing import Any

# ── Defaults ────────────────────────────────────────────────────────────────

POSTGRES_CONTAINER = os.getenv("POSTGRES_CONTAINER", "autotrading-local-postgres-1")
POSTGRES_USER      = os.getenv("POSTGRES_USER",      "autotrading")
POSTGRES_PASSWORD  = os.getenv("POSTGRES_PASSWORD",  "autotrading_dev_only")
POSTGRES_DB        = os.getenv("POSTGRES_DB",        "autotrading")
DATABASE_URL       = os.getenv("DATABASE_URL", "")


# ── SQL helpers ──────────────────────────────────────────────────────────────

def sha256_hex(raw: str) -> str:
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def _run_psql(sql: str) -> str:
    """Execute SQL and return stdout.  Raises RuntimeError on failure."""
    if DATABASE_URL:
        cmd = ["psql", DATABASE_URL, "-v", "ON_ERROR_STOP=1",
               "--no-password", "-t", "-A", "-c", sql]
        env = os.environ.copy()
    else:
        cmd = [
            "docker", "exec", "-i", POSTGRES_CONTAINER,
            "psql",
            "-U", POSTGRES_USER,
            "-d", POSTGRES_DB,
            "-v", "ON_ERROR_STOP=1",
            "-t", "-A",
            "-c", sql,
        ]
        env = {**os.environ, "PGPASSWORD": POSTGRES_PASSWORD}

    result = subprocess.run(cmd, capture_output=True, text=True, env=env)
    if result.returncode != 0:
        raise RuntimeError(
            f"psql failed (exit {result.returncode}):\n{result.stderr.strip()}"
        )
    return result.stdout


def _select(sql: str) -> list[dict[str, str]]:
    """Run a SELECT with column headers and return list of row dicts."""
    if DATABASE_URL:
        cmd = ["psql", DATABASE_URL, "--no-password", "-t", "-A",
               "--field-separator=\t", "-c", sql]
        env = os.environ.copy()
    else:
        cmd = [
            "docker", "exec", "-i", POSTGRES_CONTAINER,
            "psql",
            "-U", POSTGRES_USER,
            "-d", POSTGRES_DB,
            "-t", "-A", "--field-separator=\t",
            "-c", sql,
        ]
        env = {**os.environ, "PGPASSWORD": POSTGRES_PASSWORD}

    result = subprocess.run(cmd, capture_output=True, text=True, env=env)
    if result.returncode != 0:
        raise RuntimeError(
            f"psql query failed (exit {result.returncode}):\n{result.stderr.strip()}"
        )
    return result.stdout.strip()


# ── Pretty printing ───────────────────────────────────────────────────────────

def _table(headers: list[str], rows: list[list[str]]) -> None:
    widths = [len(h) for h in headers]
    for row in rows:
        for i, cell in enumerate(row):
            widths[i] = max(widths[i], len(str(cell)))

    sep = "  ".join("-" * w for w in widths)
    fmt = "  ".join(f"{{:<{w}}}" for w in widths)
    print(fmt.format(*headers))
    print(sep)
    for row in rows:
        print(fmt.format(*[str(c) for c in row]))
    print(f"\n{len(rows)} row(s)")


def _ok(message: str) -> None:
    print(f"✅  {message}")


def _err(message: str) -> None:
    print(f"❌  {message}", file=sys.stderr)


# ── Account commands ─────────────────────────────────────────────────────────

def account_create(account_id: str, display_name: str) -> None:
    sql = (
        f"INSERT INTO accounts (account_id, display_name, active, created_at) "
        f"VALUES ('{_esc(account_id)}', '{_esc(display_name)}', TRUE, now()) "
        f"ON CONFLICT (account_id) DO NOTHING"
    )
    _run_psql(sql)
    _ok(f"account '{account_id}' created (or already exists)")


def account_list() -> None:
    raw = _select(
        "SELECT account_id, display_name, active, "
        "to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD HH24:MI') AS created_at "
        "FROM accounts ORDER BY created_at DESC"
    )
    rows = [line.split("\t") for line in raw.splitlines() if line]
    _table(["account_id", "display_name", "active", "created_at (UTC)"], rows)


# ── Agent commands ────────────────────────────────────────────────────────────

def agent_create(agent_id: str, account_id: str, display_name: str) -> None:
    sql = (
        f"INSERT INTO agents (agent_id, account_id, display_name, active, created_at) "
        f"VALUES ('{_esc(agent_id)}', '{_esc(account_id)}', '{_esc(display_name)}', TRUE, now()) "
        f"ON CONFLICT (agent_id) DO NOTHING"
    )
    _run_psql(sql)
    _ok(f"agent '{agent_id}' created under account '{account_id}' (or already exists)")


def agent_list(account_id: str | None = None) -> None:
    where = f"WHERE account_id = '{_esc(account_id)}'" if account_id else ""
    raw = _select(
        f"SELECT agent_id, account_id, display_name, active, "
        f"to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD HH24:MI') AS created_at "
        f"FROM agents {where} ORDER BY created_at DESC"
    )
    rows = [line.split("\t") for line in raw.splitlines() if line]
    _table(["agent_id", "account_id", "display_name", "active", "created_at (UTC)"], rows)


# ── API key commands ──────────────────────────────────────────────────────────

def apikey_generate(account_id: str, generation: int = 1) -> None:
    raw_key = secrets.token_urlsafe(32)
    _apikey_insert(account_id, raw_key, generation)
    print(f"\n  ⚠  Save this key — it will NOT be shown again:\n")
    print(f"     RAW KEY : {raw_key}")
    print(f"     SHA-256 : {sha256_hex(raw_key)}")
    print(f"     HEADER  : Authorization: Bearer {raw_key}")
    print()
    _ok(f"API key (generation={generation}) created for account '{account_id}'")


def apikey_create(account_id: str, raw_key: str, generation: int = 1) -> None:
    key_hash = sha256_hex(raw_key)
    _apikey_insert(account_id, raw_key, generation)
    print(f"  SHA-256 : {key_hash}")
    _ok(f"API key (generation={generation}) registered for account '{account_id}'")


def _apikey_insert(account_id: str, raw_key: str, generation: int) -> None:
    key_hash = sha256_hex(raw_key)
    sql = (
        f"INSERT INTO account_api_keys (key_hash, account_id, generation, active, created_at) "
        f"VALUES ('{key_hash}', '{_esc(account_id)}', {int(generation)}, TRUE, now()) "
        f"ON CONFLICT (key_hash) DO NOTHING"
    )
    _run_psql(sql)


def apikey_list(account_id: str) -> None:
    raw = _select(
        f"SELECT key_hash, generation, active, "
        f"COALESCE(to_char(expires_at AT TIME ZONE 'UTC','YYYY-MM-DD'),'never') AS expires, "
        f"to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD HH24:MI') AS created_at "
        f"FROM account_api_keys WHERE account_id = '{_esc(account_id)}' "
        f"ORDER BY generation DESC, created_at DESC"
    )
    rows = [line.split("\t") for line in raw.splitlines() if line]
    _table(["key_hash (SHA-256)", "gen", "active", "expires", "created_at (UTC)"], rows)


def apikey_revoke(key_hash: str) -> None:
    sql = (
        f"UPDATE account_api_keys SET active = FALSE "
        f"WHERE key_hash = '{_esc(key_hash)}'"
    )
    _run_psql(sql)
    _ok(f"API key {key_hash[:16]}… revoked (active=FALSE)")


# ── Broker account commands ───────────────────────────────────────────────────

def broker_create(agent_id: str, external_account_id: str) -> None:
    import uuid
    broker_account_id = f"ba-{uuid.uuid4()}"
    sql = (
        f"INSERT INTO broker_accounts (broker_account_id, agent_id, broker_id, external_account_id, active, created_at) "
        f"VALUES ('{broker_account_id}', '{_esc(agent_id)}', 'ibkr', '{_esc(external_account_id)}', TRUE, now()) "
        f"ON CONFLICT (agent_id) DO UPDATE SET external_account_id = EXCLUDED.external_account_id, active = TRUE"
    )
    _run_psql(sql)
    _ok(f"broker account '{external_account_id}' mapped to agent '{agent_id}'")


def broker_list(agent_id: str | None = None) -> None:
    where = f"WHERE agent_id = '{_esc(agent_id)}'" if agent_id else ""
    raw = _select(
        f"SELECT broker_account_id, agent_id, broker_id, external_account_id, active, "
        f"to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD HH24:MI') AS created_at "
        f"FROM broker_accounts {where} ORDER BY created_at DESC"
    )
    rows = [line.split("\t") for line in raw.splitlines() if line]
    _table(["broker_account_id", "agent_id", "broker_id", "external_account_id", "active", "created_at (UTC)"], rows)


# ── Helpers ───────────────────────────────────────────────────────────────────

def _esc(s: str) -> str:
    """Minimal SQL string escape (single-quote doubling)."""
    return s.replace("'", "''")


# ── Dispatch ─────────────────────────────────────────────────────────────────

USAGE = textwrap.dedent("""\
    Usage:
      # Accounts
      onboard.py account create  <account-id> <display-name>
      onboard.py account list

      # Agents
      onboard.py agent create  <agent-id> <account-id> <display-name>
      onboard.py agent list    [<account-id>]

      # API keys
      onboard.py apikey generate  <account-id>
      onboard.py apikey create    <account-id> <raw-key> [<generation>]
      onboard.py apikey list      <account-id>
      onboard.py apikey revoke    <key-hash>

      # Broker accounts
      onboard.py broker create  <agent-id> <external-account-id>
      onboard.py broker list    [<agent-id>]

    Environment:
      POSTGRES_CONTAINER  docker container name  (default: autotrading-local-postgres-1)
      DATABASE_URL        direct psql URL        (overrides docker exec)
      POSTGRES_USER / POSTGRES_PASSWORD / POSTGRES_DB
""")


def main(argv: list[str]) -> int:
    if len(argv) < 3:
        print(USAGE)
        return 0

    resource = argv[1].lower()
    action   = argv[2].lower()
    args     = argv[3:]

    try:
        if resource == "account":
            if action == "create":
                if len(args) < 2:
                    _err("account create requires: <account-id> <display-name>")
                    return 1
                account_create(args[0], " ".join(args[1:]))
            elif action == "list":
                account_list()
            else:
                _err(f"unknown account action '{action}'"); return 1

        elif resource == "agent":
            if action == "create":
                if len(args) < 3:
                    _err("agent create requires: <agent-id> <account-id> <display-name>")
                    return 1
                agent_create(args[0], args[1], " ".join(args[2:]))
            elif action == "list":
                agent_list(args[0] if args else None)
            else:
                _err(f"unknown agent action '{action}'"); return 1

        elif resource == "apikey":
            if action == "generate":
                if not args:
                    _err("apikey generate requires: <account-id>"); return 1
                apikey_generate(args[0], int(args[1]) if len(args) > 1 else 1)
            elif action == "create":
                if len(args) < 2:
                    _err("apikey create requires: <account-id> <raw-key> [<generation>]"); return 1
                apikey_create(args[0], args[1], int(args[2]) if len(args) > 2 else 1)
            elif action == "list":
                if not args:
                    _err("apikey list requires: <account-id>"); return 1
                apikey_list(args[0])
            elif action == "revoke":
                if not args:
                    _err("apikey revoke requires: <key-hash>"); return 1
                apikey_revoke(args[0])
            else:
                _err(f"unknown apikey action '{action}'"); return 1

        elif resource == "broker":
            if action == "create":
                if len(args) < 2:
                    _err("broker create requires: <agent-id> <external-account-id>"); return 1
                broker_create(args[0], args[1])
            elif action == "list":
                broker_list(args[0] if args else None)
            else:
                _err(f"unknown broker action '{action}'"); return 1

        else:
            _err(f"unknown resource '{resource}'")
            print(USAGE)
            return 1

    except RuntimeError as exc:
        _err(str(exc))
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
