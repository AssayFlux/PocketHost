# Pocket Host — Competitive Landscape and Product Gap Analysis

## Purpose

Use existing self-hosting, mobile server, deployment and device-management products as learning references, not as a reason to copy their entire feature set into the first release.

## Product boundary

Pocket Host differentiates through a mobile-first owner experience, local-first static hosting, modular optional runtimes, a safe Workbench and eventually a verified device fabric. The first release must prove a useful narrow outcome before expanding the platform.

## Three-release priority

| Priority | Version | Product proof |
|---|---|---|
| P0 | v0.1.0 | A real website imports and serves from Android locally, over same-Wi-Fi LAN and publicly through Cloudflare Tunnel or AssayFlux Relay on a custom domain |
| P1 | v0.1.1 | Safe editing, isolated preview, versioned publishing and optional runtime integration |
| P2 | v0.1.2 | Verified devices, bounded worker roles, authenticated coordination and multi-host Relay scaling/failover |

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

Do not block first hosting on multi-device orchestration, multi-host Relay scaling, arbitrary language runtimes, full Workbench, TypeScript compilation or cross-platform fleet agents. Public hosting through Cloudflare Tunnel and the standalone owner-operated AssayFlux Relay Node is part of v0.1.0. The v0.1.0 Relay Node installs on Termux/Android; Linux-native installation is v0.1.1. No paid VPS, Docker or PostgreSQL is required, but a publicly reachable IPv4/IPv6 endpoint is.

## Proof of product

The first demonstrable vertical slice is: install APK → import a 500 MB-bounded website → start host → open locally → open from another device on same Wi-Fi → reach the real custom domain over HTTP/HTTPS through Cloudflare and a self-hosted Termux/Android Relay Node → verify assets/navigation and visitor/admin isolation → stop/restart → restore backup. Test the Relay Node from a public IPv4/IPv6 network and record CGNAT limitations. All G0–G9 gates then determine release readiness.

## Decision

The three-version roadmap is the approved implementation order. Competitive analysis does not supersede the release scope or mandatory gate policy.
