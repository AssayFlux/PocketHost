# Pocket Host — Architecture and Product Decisions

This document records decisions that remain binding unless a later ADR explicitly supersedes them.

## ADR-001 — Exactly two mobile operating modes

**Decision:** the phone always retains the website-serving role. The owner chooses Server only or Command Center + Server. Server only hides/disables fleet administration. Combined mode adds fleet management. No standalone Command Center-only mode exists.

## ADR-002 — Three-version release pathway

**Decision:** the initial delivery sequence is v0.1.0 First Hosting, v0.1.1 Workbench & Runtime Development, v0.2.0 Connected Device Fabric. The old five-step delivery sequence is retired as the release roadmap.

**Reason:** first prove a useful vertical slice before expanding into runtimes and distributed infrastructure.

## ADR-003 — Mandatory G0–G9

**Decision:** every version must document and pass all ten gates G0 through G9. A release is blocked if any gate is not PASS with evidence. See RELEASE_GATES.md.

## ADR-004 — First release is static hosting

**Decision:** v0.1.0 includes the main Android app, Rust static server, folder/ZIP import, local preview, optional LAN hosting, foreground lifecycle, backup/recovery and physical-device acceptance. It does not require Workbench, TypeScript compilation, Python/Node or public ingress.

## ADR-005 — Website import capacity

**Decision:** v0.1.0 accepts a maximum of 500,000,000 bytes for a ZIP archive and independently 500,000,000 bytes for extracted website contents. Folder imports use the extracted-content limit. File count is 5,000, total entries 10,000 and nesting depth 32. Unsafe paths, symlinks and sensitive key/secret files remain rejected.

**Separate limit:** Rust static responses remain capped at 50 MiB per file unless a later evidence-based decision changes it.

## ADR-006 — Preserve Design Language V1

**Decision:** this roadmap does not authorize a visual redesign. Keep the established monochrome visual language, calligraphic brand treatment, typography, themes, login/account layout, hamburger structure and mobile interaction model. Fix functional defects without changing brand identity.

## ADR-007 — Static serving independent of runtimes

**Decision:** static HTML/CSS/browser JavaScript must work with no runtime companion installed. Python and Node are optional separate server-side runtimes and are assigned to v0.1.1 acceptance.

## ADR-008 — Runtime implementations are existing, not complete

**Decision:** CPython 3.11 and Node.js mobile 24.22.1 already exist in runtime-worker. They currently ship together in a companion APK and share its UID. They remain in progress; do not describe them as independent downloadable packages or hardened per-project sandboxes.

## ADR-009 — Separate operator console

**Decision:** React + TypeScript + Vite in web-console is a separate browser operator client. It is not the Android main app, not a server-side runtime and not the main app TypeScript compiler.

## ADR-010 — Workbench draft/production separation

**Decision:** Workbench is v0.1.1. Draft save and local commit never mutate the live website. Publishing requires validation, owner approval, atomic activation and rollback.

## ADR-011 — Device trust is explicit

**Decision:** discovery is untrusted. Devices require unique identity, proof of possession, owner approval and exact bounded role grants. Revocation and job authorization are versioned and auditable. Production device-fabric acceptance belongs to v0.2.0.

## ADR-012 — Separate software per device class

**Decision:** each supported device OS/architecture receives a separately installable product/agent. Interoperability comes from shared protocol contracts, not from one universal binary.

## ADR-013 — Control plane is optional for local hosting

**Decision:** local static website serving must continue without the remote control plane, Relay or another device. Public ingress providers are independent and are assigned to v0.2.0 acceptance.

## ADR-014 — Evidence defines release status

**Decision:** code presence, documentation, UI presence, compilation or a simulated environment cannot close a gate that requires an installable artifact, physical device or external network. Every release stores exact commit, build, artifact digest, target, test result and reviewer.

## ADR-015 — Backup capacity is separate from import capacity

**Decision:** the 500 MB importer cap does not imply a 500 MB backup cap. The current ApplicationBackupManager allows 50 MiB for the static site root, 50 MiB for Python, 100 MiB for Node, 200 MiB total uncompressed and 220 MiB encrypted archive. This mismatch must be explicitly resolved or owner-approved as a documented limitation before v0.1.0 release.

**Reason:** import, serving, full-app backup and runtime project storage have separate quotas and memory/disk costs. They must not be described as one shared limit.
