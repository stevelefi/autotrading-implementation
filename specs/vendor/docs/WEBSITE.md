# Documentation Website (MkDocs + GitHub Pages)

## Overview
This repository publishes documentation using MkDocs Material.

- Content source: `docs/**`
- Build output: `site/`
- Site config: `mkdocs.yml`
- Python dependencies: `requirements-docs.txt`
- Deploy workflow: `.github/workflows/docs-site.yml`

## Deployment Trigger
The site deploys automatically on push to `main` when docs/build files change:
- `docs/**`
- `mkdocs.yml`
- `requirements-docs.txt`
- `overrides/**`
- `.github/workflows/docs-site.yml`

A manual run is also available with `workflow_dispatch`.

## URL
Expected GitHub Pages URL:
- `https://stevelefi.github.io/autotrading/`

## One-Time GitHub Settings
1. Open repository Settings -> Pages.
2. Ensure source is set to **GitHub Actions**.
3. Save.

## Local Preview
```bash
python3 -m venv .venv-docs
source .venv-docs/bin/activate
pip install -r requirements-docs.txt
mkdocs serve
```
Open `http://127.0.0.1:8000/autotrading/`.

## Strict Build Validation
```bash
./scripts/validate-api-contracts.sh
mkdocs build --strict
```

## Diagram Support
Mermaid diagrams are enabled via MkDocs `pymdownx.superfences` and `mermaid-init.js`.
