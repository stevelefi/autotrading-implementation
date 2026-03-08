#!/usr/bin/env python3
"""
branch_check.py — Enforces GitHub flow branch naming conventions.

Valid branch names:
  main, develop                   base branches (always allowed)
  feature/<description>           new feature work
  bugfix/<description>            bug fix targeting main/develop
  hotfix/<description>            urgent production fix
  chore/<description>             maintenance, refactor, deps
  release/<description>           release preparation
  AT-<NNNN>-<description>         Jira ticket prefix (e.g. AT-1234-add-caching)

Usage:
    python3 scripts/branch_check.py           # check the current git branch
    python3 scripts/branch_check.py <name>    # check a specific branch name
"""
from __future__ import annotations

import re
import subprocess
import sys

# ── rules ──────────────────────────────────────────────────────────────────────

VALID_EXACT = frozenset({"main", "develop"})

VALID_PREFIXES = (
    "feature/",
    "bugfix/",
    "hotfix/",
    "chore/",
    "release/",
)

# AT-<one-or-more-digits>-<at-least-one-non-whitespace-char>
JIRA_RE = re.compile(r"^AT-\d+-\S+")

# ── helpers ────────────────────────────────────────────────────────────────────


def current_branch() -> str:
    result = subprocess.run(
        ["git", "rev-parse", "--abbrev-ref", "HEAD"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        print(
            "[branch_check] ERROR: unable to determine current branch "
            "(is this a git repository?)",
            flush=True,
        )
        sys.exit(1)
    return result.stdout.strip()


def is_valid(name: str) -> bool:
    if name in VALID_EXACT:
        return True
    if any(name.startswith(p) for p in VALID_PREFIXES):
        # prefix alone (e.g. "feature/") is not a valid name — require a description
        prefix_end = name.index("/") + 1
        if len(name) > prefix_end:
            return True
        return False
    if JIRA_RE.match(name):
        return True
    return False


# ── entry point ────────────────────────────────────────────────────────────────


def main() -> None:
    branch = sys.argv[1] if len(sys.argv) > 1 else current_branch()

    if is_valid(branch):
        print(f"[branch_check] OK — '{branch}' follows GitHub flow naming.", flush=True)
        sys.exit(0)

    print(
        f"[branch_check] FAIL — branch '{branch}' does not follow GitHub flow naming.\n"
        "\n"
        "  Valid patterns:\n"
        "    feature/<description>         new feature\n"
        "    bugfix/<description>          bug fix\n"
        "    hotfix/<description>          urgent production fix\n"
        "    chore/<description>           maintenance / refactor / deps\n"
        "    release/<description>         release preparation\n"
        "    AT-<NNNN>-<description>       Jira ticket prefix\n"
        "    main, develop                 base branches\n"
        "\n"
        "  Rename your branch before committing:\n"
        "    git branch -m <new-name>\n",
        flush=True,
    )
    sys.exit(1)


if __name__ == "__main__":
    main()
