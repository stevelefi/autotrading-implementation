# 11 Config and Feature Flags

## Configuration Layers
- Static service config (startup)
- Runtime policy config (OPA bundle)
- Operational toggles (DB/system controls)

## Feature Flags (examples)
- enable_project_sync
- allow_reduce_only_mode
- enable_mnq_rollover_auto
- enable_report_branch_commit

## Change Safety
- Every config change requires audit entry.
- Production config changes use peer-review workflow.
