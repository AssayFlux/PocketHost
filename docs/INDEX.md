# PocketHost Documentation Index

Start here, in this order:

1. [North Star](NORTH_STAR.md) — what PocketHost is and what it must remain.
2. [Development Context](DEVELOPMENT_CONTEXT.md) — mandatory handoff, constraints and coding workflow.
3. [Architecture Decisions](DECISIONS.md) — accepted decisions; change them through a documented PR.
4. [Roadmap](ROADMAP.md) — sequenced work and acceptance gates.
5. [Master Plan](MASTER_PLAN.md) — architecture and technical contracts.
6. [Implementation Status](PHASE_0_STATUS.md) — what exists and what has actually been tested.
7. [Completion Milestone](COMPLETION_MILESTONE.md) — production definition of done.

## Feature and operations references

- [Adaptive Capacity, Security, Payments and Operations](ADAPTIVE_CAPACITY_SECURITY_OPERATIONS.md)
- [Runtime Companion](RUNTIME_COMPANION.md)
- [Python Runtime](PYTHON_RUNTIME.md)
- [Node Runtime](NODE_RUNTIME.md)
- [Cloudflare Tunnel](CLOUDFLARE_TUNNEL.md)
- [Remote Control Acceptance](REMOTE_CONTROL_ACCEPTANCE.md)
- [Control-plane Deployment](CONTROL_PLANE_DEPLOYMENT.md)
- [Website Deployment Guide](WEBSITE_DEPLOYMENT.md)
- [Site Backup](SITE_BACKUP.md)
- [Release Signing](RELEASE_SIGNING.md)

## Source of truth

GitHub's current source code and main commit are authoritative. This documentation explains intent and acceptance. If documentation and code differ, record the discrepancy, inspect the implementation, and update the docs in the same PR as the fix. A planned feature must never be described as implemented.


## Historical repository evidence — 2026-11-15

Main commit: `34f8d5329223d59e984e98b977e92d86e6efd555`.

Merged implementation PRs: [#18 credential hardening](https://github.com/AssayFlux/PocketHost/pull/18), [#19 website file management and SPA deployment](https://github.com/AssayFlux/PocketHost/pull/19), [#20 native biometric app unlock](https://github.com/AssayFlux/PocketHost/pull/20), [#21 password change/session rotation](https://github.com/AssayFlux/PocketHost/pull/21), [#22 device-approved Owner recovery](https://github.com/AssayFlux/PocketHost/pull/22), and [#23 BiometricPrompt compile fix](https://github.com/AssayFlux/PocketHost/pull/23).

- [Quality CI 43703703022](https://github.com/AssayFlux/PocketHost/actions/runs/43703703022): passed all six jobs on main.
- [Android integration 43703702835](https://github.com/AssayFlux/PocketHost/actions/runs/43703702835): pending at reconciliation time.

The current runtime migration and security limitations are documented in [Runtime Companion](RUNTIME_COMPANION.md), [Implementation Status](PHASE_0_STATUS.md) and [Project Baseline](PROJECT_BASELINE.md). This historical CI snapshot is not a production approval.
