# ASSAYFLUX POCKET HOST — DOCUMENTATION INDEX

This index is the navigation map for the repository's engineering, product and operations documents. Read the North Star first; use the baseline and dated status for evidence.

## Start here

1. `docs/NORTH_STAR.md` — product promise, networking strategy, authentication model and complete primary-version sequence.
2. `docs/PROJECT_BASELINE.md` — canonical implementation truth, weighted readiness and evidence rules.
3. `docs/ROADMAP.md` — ordered gates, implementation tasks and exit criteria.
4. `docs/MASTER_PLAN.md` — system architecture, module responsibilities and technical contracts.
5. `docs/DEVELOPMENT_CONTEXT.md` — mandatory constraints and contributor handoff.
6. `docs/DECISIONS.md` — accepted architecture decisions and change control.
7. `docs/PROJECT_STATUS_2026-11-16.md` — dated source/CI evidence snapshot.

## Architecture and runtime

- `docs/RUNTIME_COMPANION.md` — host/companion packages, Binder protocol, transfer and lifecycle.
- `docs/RUNTIME_ISOLATION_PROBE.md` — historical and current isolation proof work.
- `docs/RUNTIME_PROJECT_STORAGE.md` — project storage and archive handling.
- `docs/PYTHON_RUNTIME.md` — CPython 3.11 and WSGI contract.
- `docs/NODE_RUNTIME.md` — pinned Node mobile runtime and constraints.
- `docs/SECURITY_GATE_2_EXECUTION_PLAN.md` — per-project execution-cell implementation plan.
- `docs/SECURITY_ISOLATION_DESIGN.md` — threat model, security boundary and adversarial acceptance.

## Networking, identity and deployment

- `docs/CLOUDFLARE_TUNNEL.md` — reuse and validate the existing Cloudflare Tunnel.
- `docs/ASSAYFLUX_RELAY.md` — first-party relay architecture and implementation contract (planned).
- `docs/WEBSITE_DEPLOYMENT.md` — static, Python and Node deployment workflow.
- `docs/CONTROL_PLANE_DEPLOYMENT.md` — optional remote-management service; not the website origin.
- `docs/REMOTE_CONTROL_ACCEPTANCE.md` — integration and release acceptance checklist.
- `docs/management-api-v1.yaml` — local/remote management API contract.

## Capacity, operations and interface

- `docs/ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md` — device telemetry, Capacity Lab, governor, security and operations.
- `docs/SITE_BACKUP.md` — encrypted backup format, exclusions and restore.
- `docs/UI_DESIGN.md` — authoritative native UI specification.
- `docs/LEARN_GUIDE.md` — user-facing concepts and safe operating guidance.

## Build and release

- `docs/RELEASE_SIGNING.md` — signed host/companion APK process and checksums.
- `docs/COMPLETION_MILESTONE.md` — production definition of done.
- `docs/PHASE_0_STATUS.md` — implementation status and verification distinctions.
- `docs/DEVELOPMENT_CONTEXT.md` — build/test discipline and handoff rules.

## Examples and service documentation

- `examples/python-wsgi/README.md` — minimal Python WSGI example.
- `control-plane/README.md` — optional control-plane service.
- `control-plane/openapi.yaml` — remote service API.
- `docs/INDEX.md` — this catalogue.

## Documentation rules

- Source code and exact-head CI are authoritative for implementation claims.
- Mark proposals as planned; source presence is not runtime proof.
- Record exact commit SHA, workflow URL, API level, test counts, failures and skips.
- Historical snapshots must be labelled historical and must not be presented as current verification.
- When behavior changes, update this index, North Star, baseline, roadmap and affected technical guide in the same change set.
