#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path


def now_utc() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def collect_files(base: Path) -> list[Path]:
    return sorted([p for p in base.rglob("*") if p.is_file() and p.name != ".spec-manifest.json"])


def write_manifest(dest: Path) -> Path:
    files = collect_files(dest / "docs")
    payload = {
        "generated_at_utc": now_utc(),
        "root": "docs",
        "files": [
            {
                "path": str(p.relative_to(dest)).replace("\\", "/"),
                "sha256": sha256_file(p),
            }
            for p in files
        ],
    }
    manifest_path = dest / ".spec-manifest.json"
    manifest_path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    return manifest_path


def cmd_sync(args: argparse.Namespace) -> int:
    dest = Path(args.dest)
    version_file = Path(args.version_file)
    expected_dest = str(dest).replace("\\", "/")

    with tempfile.TemporaryDirectory(prefix="spec-sync-") as tmp:
        tmpdir = Path(tmp)
        checkout = tmpdir / "spec"
        subprocess.run(
            ["git", "clone", "--depth", "1", "--branch", args.ref, args.repo_url, str(checkout)],
            check=True,
        )

        src_docs = checkout / "docs"
        if not src_docs.exists():
            raise SystemExit("source docs/ not found in spec repository")

        docs_dest = dest / "docs"
        if docs_dest.exists():
            shutil.rmtree(docs_dest)
        dest.mkdir(parents=True, exist_ok=True)
        shutil.copytree(src_docs, docs_dest)

    manifest_path = write_manifest(dest)

    existing_version: dict | None = None
    if version_file.exists():
        try:
            existing_version = json.loads(version_file.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            existing_version = None

    preserve_timestamp = (
        existing_version is not None
        and existing_version.get("repo_url") == args.repo_url
        and existing_version.get("ref") == args.ref
        and existing_version.get("dest") == expected_dest
        and bool(existing_version.get("synced_at_utc"))
    )

    version_payload = {
        "repo_url": args.repo_url,
        "ref": args.ref,
        "synced_at_utc": (
            existing_version["synced_at_utc"] if preserve_timestamp else now_utc()
        ),
        "dest": expected_dest,
    }
    serialized_version = json.dumps(version_payload, indent=2) + "\n"
    if not version_file.exists() or version_file.read_text(encoding="utf-8") != serialized_version:
        version_file.write_text(serialized_version, encoding="utf-8")

    print(f"synced spec docs to {docs_dest}")
    print(f"wrote manifest: {manifest_path}")
    print(f"wrote version file: {version_file}")
    return 0


def cmd_verify(args: argparse.Namespace) -> int:
    dest = Path(args.dest)
    version_file = Path(args.version_file)
    manifest_file = dest / ".spec-manifest.json"

    errors: list[str] = []

    if not version_file.exists():
        errors.append(f"missing version file: {version_file}")
    else:
        version = json.loads(version_file.read_text(encoding="utf-8"))
        expected_dest = str(dest).replace("\\", "/")
        if version.get("dest") != expected_dest:
            errors.append(
                f"SPEC_VERSION.json dest mismatch: expected {expected_dest}, found {version.get('dest')}"
            )

    if not manifest_file.exists():
        errors.append(f"missing manifest file: {manifest_file}")
    else:
        manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
        expected = {entry["path"]: entry["sha256"] for entry in manifest.get("files", [])}

        current_files = collect_files(dest / "docs") if (dest / "docs").exists() else []
        actual = {
            str(p.relative_to(dest)).replace("\\", "/"): sha256_file(p)
            for p in current_files
        }

        missing = sorted(set(expected) - set(actual))
        extra = sorted(set(actual) - set(expected))
        mismatched = sorted(k for k in expected.keys() & actual.keys() if expected[k] != actual[k])

        if missing:
            errors.append(f"manifest missing files: {missing[:10]}")
        if extra:
            errors.append(f"unexpected files since last sync: {extra[:10]}")
        if mismatched:
            errors.append(f"hash mismatch files: {mismatched[:10]}")

    if errors:
        print("spec verify failed:")
        for err in errors:
            print(f"- {err}")
        return 1

    print("spec verify passed")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Sync and verify pinned spec docs")
    sub = parser.add_subparsers(dest="command", required=True)

    p_sync = sub.add_parser("sync", help="sync docs from spec repository")
    p_sync.add_argument("--repo-url", required=True)
    p_sync.add_argument("--ref", required=True)
    p_sync.add_argument("--dest", required=True)
    p_sync.add_argument("--version-file", required=True)
    p_sync.set_defaults(func=cmd_sync)

    p_verify = sub.add_parser("verify", help="verify vendored docs against local manifest")
    p_verify.add_argument("--dest", required=True)
    p_verify.add_argument("--version-file", required=True)
    p_verify.set_defaults(func=cmd_verify)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
