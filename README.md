# autotrading-implementation

Implementation monorepo for trading services, reliability libraries, and database migrations.

## Spec Baseline
- Spec repo: https://github.com/stevelefi/autotrading.git
- Pinned ref: spec-v1.0.1-m0m1
- Local sync path (generated, gitignored): specs/vendor/docs

## Commands

    python tools/spec_sync.py sync --repo-url https://github.com/stevelefi/autotrading.git --ref spec-v1.0.1-m0m1 --dest specs/vendor --version-file SPEC_VERSION.json
    python tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json
