# Pocket Host — Release Completion Contract

**Baseline:** 2026-11-16 · `a8988f5068f4a65206b89451e8204b51b86abc55`  
**Status:** not complete; integrated development software.

## 1. Companion runtime migration

- Python and Node execute inside the separate-UID companion.
- Authenticated/versioned IPC, bounded transfer and scoped project capabilities.
- Worker lifecycle, readiness, crash recovery, bounded restart and update/rollback.
- Rust routes both runtime types through companion; static origin survives worker failure.
- API 26/33/35 end-to-end tests pass.

## 2. Isolation and security

- Worker cannot read host credentials, management secrets or unrelated site data.
- Egress restrictions cover raw sockets, DNS rebinding, private/link-local/metadata destinations and management loopback.
- Adversarial archive, traversal, symlink, SSRF, cross-project, protocol-abuse and exhaustion tests pass.
- Android resource-control limitations are documented accurately.
- Independent security findings are resolved or explicitly dispositioned.

## 3. Rust governor and Capacity Lab

- Fair global/per-site scheduling, bounded queues, static-origin reserve and overload behavior verified.
- Slow-client, cancellation, upstream failure and graceful-drain tests pass.
- Capacity Lab covers static cache/payload classes and optional trusted runtime workloads.
- Reports include throughput, p50/p95/p99, errors and device-resource observations with repeatability and abort evidence.
- No unsupported universal capacity claim.

## 4. Backup, operations and Settings

- Restore is staged and recoverable after interruption, corruption and low storage.
- Wrong passphrase, malformed archive, custom roots and rollback failures are tested.
- Secrets excluded/redacted; logs and reports remain bounded.
- Operations evidence survives restart and missing telemetry.
- Settings passes screenshot, responsive, accessibility and navigation acceptance.

## 5. CI and deliverables

- Quality workflow and API 26/33/35 matrix pass on the same exact candidate SHA.
- Host and companion APKs built for declared supported ABIs.
- Test reports, screenshots/hierarchy, provenance and checksums retained.
- Downloaded artifacts are validated; a listed or truncated artifact is not acceptance.
- Upgrade/install path verified.

## 6. Release and external acceptance

- Signed host and companion release APKs verify against owner-controlled signing certificate.
- SHA-256 checksums and release notes published.
- Existing Cloudflare Tunnel serves external HTTPS; hostname routes only to Rust website listener.
- Management API is not reachable from public ingress.
- Physical Android device acceptance recorded.
- Independent security review complete and release owner approves.

## Guardrails

Static hosting remains independent of Python, Node, remote control and payments. No Termux/root dependency. Python/Node remain trusted-code-only until isolation gate closes. Do not claim hard CPU quotas where Android provides no enforceable control. Do not infer external deployment or device acceptance from CI. Do not publish debug APKs as production releases.

**Completion rule:** every P0 criterion must have implementation, tests, exact-SHA CI evidence and required external/owner acceptance. The canonical score and gate state live in [PROJECT_BASELINE.md](PROJECT_BASELINE.md). Documentation-only updates do not increase implementation readiness.
