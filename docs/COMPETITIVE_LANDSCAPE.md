# Pocket Host — Competitive Landscape and Product Gap Analysis

## Purpose

Use existing self-hosting, mobile server, deployment and device-management products as learning references, not as a reason to copy their entire feature set into the first release.

## Product boundary

Pocket Host differentiates through a mobile-first owner experience, local-first static hosting, modular optional runtimes, a safe Workbench and eventually a verified device fabric. The first release must prove a useful narrow outcome before expanding the platform.

## Three-release priority

| Priority | Version | Product proof |
|---|---|---|
| P0 | v0.1.0 | A real website imports and serves from Android locally and over same-Wi-Fi LAN |
| P1 | v0.1.1 | Safe editing, isolated preview, versioned publishing and optional runtime integration |
| P2 | v0.2.0 | Verified devices, bounded worker roles, authenticated coordination and public ingress |

## Lessons to adopt

- Keep onboarding short and make the first success observable.
- Show the exact local/LAN address and active service state.
- Make imports transactional and preserve the prior working site.
- Provide clear logs, diagnostics and recovery.
- Separate drafts from production deployments.
- Make resource, storage and network limits visible.
- Treat remote access as a separate security boundary.
- Never give a worker broad filesystem, network or runtime privileges by default.
- Publish a truthful support matrix and reproducible release evidence.

## Explicit non-goals for v0.1.0

Do not block first hosting on multi-device orchestration, public relay, arbitrary language runtimes, full Workbench, TypeScript compilation or cross-platform agents. Do not imply these features are complete because partial source exists.

## Proof of product

The first demonstrable vertical slice is: install APK → import a 500 MB-bounded website → start host → open locally → open from another device on same Wi-Fi → verify assets/navigation → stop/restart → restore backup. All G0–G9 gates then determine release readiness.

## Decision

The three-version roadmap is the approved implementation order. Competitive analysis does not supersede the release scope or mandatory gate policy.
