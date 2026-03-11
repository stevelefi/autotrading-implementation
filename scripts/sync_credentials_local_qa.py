#!/usr/bin/env python3
"""
sync_credentials_local_qa.py

Create the same onboarding credentials in local and QA environments:
- account
- agent
- broker account mapping
- API key (same raw key in both environments)

This script wraps scripts/onboard.py and leverages DATABASE_URL targeting.

Examples
--------
python3 scripts/sync_credentials_local_qa.py \
  --account-id acct-qa-demo \
  --account-name "QA Demo Account" \
  --agent-id agent-qa-demo \
  --agent-name "QA Demo Agent" \
  --broker-external-account DU1234567 \
  --qa-database-url "postgresql://user:pass@qa-host:5432/autotrading"

python3 scripts/sync_credentials_local_qa.py \
  --account-id acct-qa-demo \
  --account-name "QA Demo Account" \
  --agent-id agent-qa-demo \
  --agent-name "QA Demo Agent" \
  --broker-external-account DU1234567 \
  --api-key "paste-existing-raw-key" \
  --qa-database-url "$QA_DATABASE_URL"
"""
from __future__ import annotations

import argparse
import os
import secrets
import subprocess
import sys
from dataclasses import dataclass


@dataclass
class EnvTarget:
    name: str
    env: dict[str, str]


def run_onboard(target: EnvTarget, *args: str) -> None:
    cmd = [sys.executable, "scripts/onboard.py", *args]
    result = subprocess.run(cmd, capture_output=True, text=True, env=target.env)
    if result.returncode != 0:
        raise RuntimeError(
            f"[{target.name}] onboard command failed: {' '.join(args)}\n"
            f"stdout:\n{result.stdout}\n"
            f"stderr:\n{result.stderr}"
        )


def build_targets(qa_database_url: str) -> tuple[EnvTarget, EnvTarget]:
    local_env = os.environ.copy()
    local_env.pop("DATABASE_URL", None)

    qa_env = os.environ.copy()
    qa_env["DATABASE_URL"] = qa_database_url

    return EnvTarget("local", local_env), EnvTarget("qa", qa_env)


def sync_credentials(
    account_id: str,
    account_name: str,
    agent_id: str,
    agent_name: str,
    broker_external_account: str,
    api_key: str,
    api_key_generation: int,
    qa_database_url: str,
) -> None:
    local_target, qa_target = build_targets(qa_database_url)

    for target in (local_target, qa_target):
        run_onboard(target, "account", "create", account_id, account_name)
        run_onboard(target, "agent", "create", agent_id, account_id, agent_name)
        run_onboard(target, "broker", "create", agent_id, broker_external_account)
        run_onboard(target, "apikey", "create", account_id, api_key, str(api_key_generation))

    print("✅ Credential parity sync completed")
    print(f"   account_id: {account_id}")
    print(f"   agent_id:   {agent_id}")
    print(f"   api_key_generation: {api_key_generation}")
    print("\nUse this header in both local and QA:")
    print(f"Authorization: Bearer {api_key}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Sync onboarding credentials to local + QA")
    parser.add_argument("--account-id", required=True)
    parser.add_argument("--account-name", required=True)
    parser.add_argument("--agent-id", required=True)
    parser.add_argument("--agent-name", required=True)
    parser.add_argument("--broker-external-account", required=True)
    parser.add_argument(
        "--api-key",
        required=False,
        help="Raw API key to register in both envs. If omitted, a random key is generated.",
    )
    parser.add_argument(
        "--api-key-generation",
        type=int,
        default=1,
        help="API key generation number (default: 1)",
    )
    parser.add_argument(
        "--qa-database-url",
        default=os.getenv("QA_DATABASE_URL", ""),
        help="PostgreSQL URL for QA (or set QA_DATABASE_URL env var)",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    if not args.qa_database_url:
        print("❌ Missing --qa-database-url (or QA_DATABASE_URL environment variable)", file=sys.stderr)
        return 1

    raw_api_key = args.api_key or secrets.token_urlsafe(32)

    try:
        sync_credentials(
            account_id=args.account_id,
            account_name=args.account_name,
            agent_id=args.agent_id,
            agent_name=args.agent_name,
            broker_external_account=args.broker_external_account,
            api_key=raw_api_key,
            api_key_generation=args.api_key_generation,
            qa_database_url=args.qa_database_url,
        )
    except RuntimeError as exc:
        print(f"❌ {exc}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
