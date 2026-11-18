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

**Decision:** v0.1.0 includes the main Android app, Rust static server, folder/ZIP import, local preview, optional LAN hosting, Cloudflare Tunnel, first-party AssayFlux Relay, custom-domain HTTP/HTTPS, foreground lifecycle, backup/recovery and physical-device/external-network acceptance. It does not require Workbench, TypeScript compilation, Python/Node or the full fleet control plane.

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

**Decision:** local static website serving must continue without the remote control plane, Relay or another device. Cloudflare Tunnel and AssayFlux Relay are optional independent v0.1.0 public-ingress providers. A provider failure must not stop local/LAN hosting or the other provider. Full fleet orchestration and multi-host Relay scaling remain v0.2.0.

## ADR-014 — Evidence defines release status

**Decision:** code presence, documentation, UI presence, compilation or a simulated environment cannot close a gate that requires an installable artifact, physical device or external network. Every release stores exact commit, build, artifact digest, target, test result and reviewer.

## ADR-016 — Public visitor ingress ships in v0.1.0

**Decision:** v0.1.0 includes two independent public website ingress paths: Cloudflare Tunnel and the first-party AssayFlux Relay. Both must support owner-verified custom hostnames and HTTP/HTTPS from external networks. HTTPS is valid at the edge; HTTP redirects to HTTPS by default, and plain HTTP content serving is an explicit owner opt-in.

**Reason:** the first hosting release must be capable of serving the owner's real domain from any visitor device, not only localhost or the same Wi-Fi network. This does not make the full device control plane a v0.1.0 dependency.

## ADR-017 — Relay gateway is a visitor-only service

**Decision:** the current Relay source is only a foundation and must not be treated as the finished v0.1.0 product. v0.1.0 requires a standalone owner-operated Relay Node, separate from the fleet control-plane process, with dedicated host-ingress identity and visitor-only routing. It must install on declared Termux/Android and Linux targets without mandatory Docker or PostgreSQL. Public visitor routes must never reach admin/device/runtime/MCU endpoints. Multi-host scheduling, regional scale and automated failover are v0.2.0.

## ADR-015 — Backup capacity is separate from import capacity

**Original decision:** the 500 MB importer cap did not imply a 500 MB backup cap. At the time, ApplicationBackupManager allowed 50 MiB for the static site root and 200 MiB total uncompressed.

**Superseding implementation:** the current source now allows a 500,000,000-byte static-site root, 650,000,000 bytes total uncompressed and a 680,000,000-byte encrypted archive. Python and Node roots remain separately capped at 50 MiB and 100 MiB. The static-site backup cap now matches the importer expanded-content cap. Near-limit backup/restore and physical-device free-space/recovery tests remain mandatory.

**Reason:** import, serving, full-app backup and runtime project storage have separate quotas and memory/disk costs. They must not be described as one shared limit.


## ADR-018 — AssayFlux Relay is owner-operated; no paid VPS prerequisite

**Decision:** v0.1.0 must provide a standalone AssayFlux Relay Node installable on owner-controlled Termux/Android and Linux hardware. It must not require a paid VPS, Cloudflare, Docker, PostgreSQL or the full fleet control plane. The Android website host connects outbound using independent, revocable Relay credentials. The Relay Node accepts public visitors and forwards only to the registered website route.

**Network constraint:** a self-hosted public gateway still needs public IPv4 with router port forwarding or globally routed IPv6, public DNS A/AAAA and an allowed inbound path for the visitor listener. A device behind CGNAT without global IPv6 or port forwarding cannot accept inbound public visitors. The website-host phone may remain behind CGNAT because its Relay connection is outbound. If no owner-controlled reachable node exists, Cloudflare Tunnel remains an optional fallback; the product must never imply that DNS or application code alone bypasses CGNAT.

**Current-source status:** the repository has a partial Relay client and gateway, but no standalone Termux package/installer. The client is coupled to RemoteControlAgent credentials; the gateway shares the fleet control-plane executable and PostgreSQL. This ADR defines the target architecture, not a claim that it is implemented.
