#!/usr/bin/env python3
"""
pr.py — Safe branch + commit + push + GitHub PR creation.

All subprocess calls use argument lists (never shell=True), eliminating
shell-quoting issues (dquote> traps, multiline string problems, etc.).

Usage:
    # Create a new branch, stage everything, commit, push, open PR
    python3 scripts/pr.py \\
        --branch feature/my-feature \\
        --title "feat(scope): one-line summary" \\
        --body "Describe what changed and why." \\
        [--base main] [--draft] [--no-push] [--body-file PATH]

    # Just stage + commit on the current branch (no push/PR)
    python3 scripts/pr.py --commit-only \\
        --title "chore: tidy up"

    # Push + create PR on the current branch (already committed)
    python3 scripts/pr.py --push-only \\
        --title "feat(risk): add retry logic" \\
        --body "Adds exponential backoff to the risk gRPC client."

Flags:
    --branch BRANCH       Branch to create and push (default: current branch)
    --base BRANCH         Base branch for the PR (default: main)
    --title TEXT          PR title / commit subject (required unless --push-only)
    --body TEXT           PR body text (inline)
    --body-file PATH      Read PR body from a file (overrides --body)
    --draft               Open the PR as a draft
    --commit-only         Stage + commit, then stop (no push, no PR)
    --push-only           Push the current branch and create a PR; skip branch
                          creation and commit (assumes already committed)
    --no-create-pr        Push the branch but do not create a PR
    --add-files FILES...  Specific files to stage (default: git add --all)
    --amend               Amend the last commit instead of creating a new one

Notes:
    • Validates branch name with scripts/branch_check.py before doing anything.
    • Writes the PR body to a temp file so multiline text is safe.
    • Requires the GitHub CLI (gh) to be installed and authenticated for PR
      creation.  Install: https://cli.github.com/
"""
from __future__ import annotations

import argparse
import subprocess
import sys
import tempfile
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


# ── helpers ────────────────────────────────────────────────────────────────────

def _run(args: list[str], *, check: bool = True, capture: bool = False) -> subprocess.CompletedProcess:
    """Run a command as a list — never shell=True."""
    print(f"\n$ {' '.join(args)}", flush=True)
    return subprocess.run(
        args,
        cwd=ROOT,
        check=check,
        capture_output=capture,
        text=capture,
    )


def _run_output(args: list[str]) -> str:
    result = _run(args, check=True, capture=True)
    return result.stdout.strip()


def _current_branch() -> str:
    return _run_output(["git", "rev-parse", "--abbrev-ref", "HEAD"])


def _branch_exists_remote(branch: str) -> bool:
    result = subprocess.run(
        ["git", "ls-remote", "--heads", "origin", branch],
        cwd=ROOT, capture_output=True, text=True,
    )
    return bool(result.stdout.strip())


def _validate_branch(branch: str) -> None:
    print(f"\n[pr.py] Validating branch name: {branch}", flush=True)
    result = subprocess.run(
        [sys.executable, "scripts/branch_check.py", branch],
        cwd=ROOT,
    )
    if result.returncode != 0:
        print(
            f"\n[pr.py] ERROR: '{branch}' does not satisfy branch naming rules.\n"
            "         See scripts/branch_check.py for valid patterns.",
            flush=True,
        )
        sys.exit(1)


def _gh_available() -> bool:
    result = subprocess.run(["gh", "--version"], capture_output=True)
    return result.returncode == 0


# ── steps ──────────────────────────────────────────────────────────────────────

def step_create_branch(branch: str, base: str) -> None:
    """Create and switch to a new local branch from base."""
    print(f"\n[pr.py] Creating branch '{branch}' from '{base}'", flush=True)
    # Fetch latest base to avoid stale refs
    _run(["git", "fetch", "origin", base], check=False)
    _run(["git", "checkout", "-b", branch, f"origin/{base}"])


def step_stage(files: list[str] | None) -> None:
    """Stage files (all by default)."""
    if files:
        _run(["git", "add", "--"] + files)
    else:
        _run(["git", "add", "--all"])


def step_commit(title: str, body: str = "", *, amend: bool = False) -> None:
    """Create or amend a commit."""
    message = title if not body else f"{title}\n\n{body}"
    cmd = ["git", "commit"]
    if amend:
        cmd.append("--amend")
    # Use -m for each paragraph to avoid any quoting edge cases
    lines = message.split("\n\n")
    for line in lines:
        cmd += ["-m", line]
    _run(cmd)


def step_push(branch: str, *, force: bool = False) -> None:
    """Push branch to origin."""
    cmd = ["git", "push", "--set-upstream", "origin", branch]
    if force:
        cmd.append("--force-with-lease")
    _run(cmd)


def step_create_pr(
    title: str,
    body: str,
    base: str,
    *,
    draft: bool = False,
) -> None:
    """Create a GitHub PR using gh CLI. Body is written to a temp file."""
    if not _gh_available():
        print(
            "\n[pr.py] WARNING: 'gh' CLI not found. Skipping PR creation.\n"
            "         Install from: https://cli.github.com/\n"
            "         Then run: gh pr create --base {base} --title '<title>'",
            flush=True,
        )
        return

    # Write body to a temp file — avoids ALL shell quoting issues
    with tempfile.NamedTemporaryFile(
        mode="w", suffix=".md", delete=False, prefix="pr_body_"
    ) as f:
        f.write(body)
        body_file = f.name

    print(f"\n[pr.py] PR body written to: {body_file}", flush=True)

    cmd = [
        "gh", "pr", "create",
        "--base", base,
        "--title", title,
        "--body-file", body_file,
    ]
    if draft:
        cmd.append("--draft")

    result = _run(cmd, check=False)
    if result.returncode != 0:
        print(
            "\n[pr.py] gh pr create failed. You can create the PR manually:\n"
            f"         gh pr create --base {base} --title '{title}' "
            f"--body-file {body_file}",
            flush=True,
        )
        sys.exit(result.returncode)


# ── main ───────────────────────────────────────────────────────────────────────

def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="Create a branch, commit, push, and open a GitHub PR.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=textwrap.dedent("""
        Examples:
          # Full workflow — new branch + commit + PR
          python3 scripts/pr.py \\
              --branch feature/my-feature \\
              --title "feat(scope): short summary" \\
              --body "What changed and why."

          # Already on the right branch, just push + PR
          python3 scripts/pr.py --push-only \\
              --title "fix(order): correct timeout threshold"

          # Just commit (no push, no PR)
          python3 scripts/pr.py --commit-only \\
              --title "chore: clean up logs"

          # Stage specific files only
          python3 scripts/pr.py \\
              --branch chore/update-deps \\
              --title "chore(deps): bump spring-boot to 3.3.6" \\
              --add-files pom.xml libs/kafka-client/pom.xml
        """),
    )

    # Branch
    p.add_argument("--branch", metavar="NAME",
                   help="Branch name to create (default: use current branch)")
    p.add_argument("--base", default="main", metavar="BRANCH",
                   help="Base branch for the PR (default: main)")

    # Commit content
    p.add_argument("--title", metavar="TEXT",
                   help="Commit subject / PR title (required unless --push-only)")
    p.add_argument("--body", default="", metavar="TEXT",
                   help="PR body text (inline)")
    p.add_argument("--body-file", metavar="PATH",
                   help="Read PR body from a file (overrides --body)")
    p.add_argument("--add-files", nargs="+", metavar="FILE",
                   help="Files to stage (default: git add --all)")
    p.add_argument("--amend", action="store_true",
                   help="Amend the last commit instead of creating a new one")

    # Mode switches
    p.add_argument("--commit-only", action="store_true",
                   help="Stage + commit, then stop (no push, no PR)")
    p.add_argument("--push-only", action="store_true",
                   help="Push current branch + create PR, skip branch creation and commit")
    p.add_argument("--no-create-pr", action="store_true",
                   help="Push branch but skip PR creation")
    p.add_argument("--draft", action="store_true",
                   help="Open the PR as a draft")
    p.add_argument("--force-push", action="store_true",
                   help="Push with --force-with-lease (useful after --amend)")

    return p


def main() -> None:
    p = build_parser()
    args = p.parse_args()

    # ── resolve branch ────────────────────────────────────────────────────────
    if args.branch:
        branch = args.branch
    else:
        branch = _current_branch()
        print(f"\n[pr.py] Using current branch: {branch}", flush=True)

    _validate_branch(branch)

    # ── resolve PR body ───────────────────────────────────────────────────────
    body = args.body or ""
    if args.body_file:
        body = Path(args.body_file).read_text()

    # ── workflow ──────────────────────────────────────────────────────────────
    if args.push_only:
        # Already committed; just push + PR
        step_push(branch, force=args.force_push)
        if not args.no_create_pr:
            if not args.title:
                p.error("--title is required when creating a PR")
            step_create_pr(args.title, body, args.base, draft=args.draft)
        return

    # Ensure title is provided for commit + PR workflows
    if not args.title:
        p.error("--title is required (used as commit subject and PR title)")

    # Create branch if explicitly specified and it is not the current branch
    current = _current_branch()
    if args.branch and args.branch != current:
        step_create_branch(branch, args.base)

    # Stage
    step_stage(args.add_files)

    # Commit
    step_commit(args.title, body, amend=args.amend)

    if args.commit_only:
        print("\n[pr.py] --commit-only: stopping after commit.", flush=True)
        return

    # Push
    step_push(branch, force=args.force_push)

    # PR
    if not args.no_create_pr:
        step_create_pr(args.title, body, args.base, draft=args.draft)

    print(f"\n[pr.py] Done. Branch: {branch}", flush=True)


if __name__ == "__main__":
    main()
