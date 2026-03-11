#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
from pathlib import Path


def update_image_block(file_path: Path, repository: str, tag: str, digest: str = "") -> None:
    content = file_path.read_text(encoding="utf-8").splitlines()

    in_image = False
    found_repository = False
    found_tag = False
    found_digest = False
    updated_lines: list[str] = []

    digest_value = '""' if digest == "" else digest

    for line in content:
        if re.match(r"^image:\s*$", line):
            in_image = True
            updated_lines.append(line)
            continue

        if in_image and re.match(r"^\S", line):
            in_image = False

        if in_image and re.match(r"^\s{2}repository:\s*", line):
            updated_lines.append(f"  repository: {repository}")
            found_repository = True
            continue

        if in_image and re.match(r"^\s{2}tag:\s*", line):
            updated_lines.append(f"  tag: {tag}")
            found_tag = True
            continue

        if in_image and re.match(r"^\s{2}digest:\s*", line):
            updated_lines.append(f"  digest: {digest_value}")
            found_digest = True
            continue

        updated_lines.append(line)

        if in_image and found_repository and found_tag and not found_digest and re.match(r"^\s{2}pullPolicy:\s*", line):
            updated_lines.insert(len(updated_lines) - 1, f"  digest: {digest_value}")
            found_digest = True

    if not found_repository or not found_tag:
        raise SystemExit(
            f"Failed to locate image.repository/tag in {file_path}. "
            "Expected an 'image:' block with 'repository' and 'tag' keys."
        )

    file_path.write_text("\n".join(updated_lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="Update image.repository and image.tag in a Helm values file")
    parser.add_argument("--file", required=True, help="Path to values file")
    parser.add_argument("--repository", required=True, help="Container image repository")
    parser.add_argument("--tag", required=True, help="Container image tag")
    parser.add_argument("--digest", default="", help="Container image digest (sha256:...) for immutable deploys")
    args = parser.parse_args()

    values_file = Path(args.file)
    if not values_file.exists():
        raise SystemExit(f"Values file not found: {values_file}")

    update_image_block(values_file, args.repository, args.tag, args.digest)


if __name__ == "__main__":
    main()
