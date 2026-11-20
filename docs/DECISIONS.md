# Pocket Host — Architecture and Product Decisions

This document records decisions that remain binding unless a later ADR explicitly supersedes them.

## ADR-001 — Exactly two mobile operating modes

**Decision:** the phone always retains the website-serving role. The owner chooses Server only or Command Center + Server. Server only hides/disables fleet administration. Combined mode adds fleet management. No standalone Command Center-only mode exists.

## ADR-002 — Three-version release pathway

**Decision:** the initial delivery sequence is v0.1.0 First Hosting, v0.1.1 Workbench, Runtime & Linux Relay Installation, v0.1.2 Connected Device Fabric. The old five-step delivery sequence is retired as the release roadmap.

**Reason:** first prove a useful vertical slice before expanding into runtimes and distributed infrastructure.

## ADR-003 — Separate engineering tests from APK packaging

**Decision:** normal code pushes and pull requests run quality checks, Android compilation, native ABI builds and emulator tests. APK packaging is a separate manually triggered workflow and is not coupled to routine code verification.

## ADR-004 — First release is static hosting

**Decision:** v0.1.0 includes the main Android app, Rust static server, folder/ZIP import, local preview, optional LAN hosting, Cloudflare Tunnel, first-party AssayFlux Relay, custom-domain HTTP/HTTPS, foreground lifecycle, backup/recovery and physical-device/external-network acceptance. It does not require Workbench, TypeScript compilation, Python/Node or the full fleet control plane.

## ADR-005 — Website import capacity

**Decision:** v0.1.0 accepts a maximum of 500,000,000 bytes for a ZIP archive and independently 500,000,000 bytes for extracted website contents. Folder imports use the extracted-content limit. File count is 5,000, total entries 10,000 and nesting depth 32. Unsafe paths, symlinks and sensitive key/secret files remain rejected.

**Separate limit:** Rust static responses are capped at 500,000,000 bytes (500 MB decimal) per file; manual single-file import is capped at 50 MiB and text editing at 256 KiB unless a later evidence-based decision changes it.

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

**Decision:** discovery is untrusted. Devices require unique identity, proof of possession, owner approval and exact bounded role grants. Revocation and job authorization are versioned and auditable. Production device-fabric acceptance belongs to v0.1.2.

## ADR-012 — Separate software per device class

**Decision:** each supported device OS/architecture receives a separately installable product/agent. Interoperability comes from shared protocol contracts, not from one universal binary.

## ADR-013 — Control plane is optional for local hosting

**Decision:** local static website serving must continue without the remote control plane, Relay or another device. Cloudflare Tunnel and AssayFlux Relay are optional independent v0.1.0 public-ingress providers. A provider failure must not stop local/LAN hosting or the other provider. Full fleet orchestration and multi-host Relay scaling remain v0.1.2.

## ADR-014 — Report test evidence accurately

**Decision:** source presence, compilation, emulator tests and physical-device behavior are different evidence. Report exactly what ran and passed; do not claim physical-device behavior from emulator results.

## ADR-016 — Public visitor ingress ships in v0.1.0

**Decision:** v0.1.0 includes two independent public website ingress paths: Cloudflare Tunnel and the first-party AssayFlux Relay. Both must support owner-verified custom hostnames and HTTP/HTTPS from external networks. HTTPS is valid at the edge; HTTP redirects to HTTPS by default, and plain HTTP content serving is an explicit owner opt-in.

**Reason:** the first hosting release must be capable of serving the owner's real domain from any visitor device, not only localhost or the same Wi-Fi network. This does not make the full device control plane a v0.1.0 dependency.

## ADR-017 — Relay gateway is a visitor-only service

**Decision:** the current Relay source is only a foundation and must not be treated as the finished v0.1.0 product. v0.1.0 requires a visitor-only Relay service independent of the fleet control-plane process, with dedicated host-ingress identity and credentials. Its deployment architecture is superseded by ADR-020: a Kotlin foreground service and Rust/Tokio JNI engine bundled in the Pocket Host Android APK. The node must not require Docker or PostgreSQL. Public visitor routes must never reach admin/device/runtime/MCU endpoints. Multi-host scheduling, regional scale and automated failover are v0.1.2.

## ADR-015 — Backup capacity is separate from import capacity

**Original decision:** the 500 MB importer cap did not imply a 500 MB backup cap. At the time, ApplicationBackupManager allowed 50 MiB for the static site root and 200 MiB total uncompressed.

**Superseding implementation:** the current source now allows a 500,000,000-byte static-site root, 650,000,000 bytes total uncompressed and a 680,000,000-byte encrypted archive. Python and Node roots remain separately capped at 50 MiB and 100 MiB. The static-site backup cap now matches the importer expanded-content cap. Near-limit backup/restore and physical-device free-space/recovery tests remain mandatory.

**Reason:** import, serving, full-app backup and runtime project storage have separate quotas and memory/disk costs. They must not be described as one shared limit.


## ADR-018 — AssayFlux Relay is owner-operated; no paid VPS prerequisite

**Original decision (superseded by ADR-020):** v0.1.0 must provide an owner-operated AssayFlux Relay Node independent of the full fleet control plane. The owner-operated node still requires a publicly reachable network path; it must not require a paid VPS, Docker or PostgreSQL. The Android website host connects outbound using independent, revocable Relay credentials, and the Relay accepts public visitors only for registered website routes.

**Network constraint:** a self-hosted public gateway still needs public IPv4 with router port forwarding or globally routed IPv6, public DNS A/AAAA and an allowed inbound path for the visitor listener. A device behind CGNAT without global IPv6 or port forwarding cannot accept inbound public visitors. The website-host phone may remain behind CGNAT because its Relay connection is outbound. If no owner-controlled reachable node exists, Cloudflare Tunnel remains an optional fallback; the product must never imply that DNS or application code alone bypasses CGNAT.

**Current-source status:** the repository has a partial Relay client and gateway, but no native Android JNI engine or foreground service. The Android client uses independent RelayCredentialStore credentials and HostingService lifecycle; the gateway still shares the fleet control-plane executable and PostgreSQL and has no standalone node-side credential issuer. This ADR defines the target architecture, not a claim that it is implemented.


## ADR-019 — Corrected three-release numbering and immutable v0.1.0 baseline

**Decision:** the approved release sequence is exactly v0.1.0 First Hosting → v0.1.1 Workbench, Runtime & Linux Relay Installation → v0.1.2 Connected Device Fabric. The previous label v0.2.0 for the connected-device milestone is retired and renumbered to v0.1.2. No separate v0.2.0 milestone remains in the initial three-release pathway.

**Baseline rule:** when v0.1.0 is ready to preserve, record its source commit and tested artifact. Keep later feature work separate from that baseline; do not rewrite an established version tag.


## ADR-020 — Native Android Relay service replaces external terminal deployment

**Status:** approved by the project owner on 2026-11-18. This ADR supersedes the deployment mechanism described by ADR-017/ADR-018 and all earlier v0.1.0 Relay installation notes.

**Decision:** implement AssayFlux Relay natively inside the Pocket Host Android APK. The Android layer is a Kotlin foreground service responsible for owner controls, Android lifecycle, notifications, permissions, network state, recovery and Android Keystore integration. The data plane is a Rust/Tokio engine built for the declared Android ABIs, packaged as a native library and controlled through JNI. The Relay supports both inbound public visitor HTTP(S) and outbound authenticated host sessions. The existing host-side RelayAgent wire contract is the compatibility baseline unless deliberately versioned.

**No external runtime dependency:** users must not need to install or operate a terminal application, a JavaScript runtime, npm, Docker, PostgreSQL or a separate Linux userspace to run the v0.1.0 Android Relay. The native engine is bundled in the main APK; it is not a separately terminal-managed installation or a separate APK.

**Trust and isolation:** node identity and host-ingress credentials are independent of fleet admin, RemoteControlAgent and Cloudflare credentials. Host credentials are high-entropy, scoped, revocable and stored as verifiers on the node; host-side secrets are protected by Android Keystore. Public visitor routes may reach only verified website routes. Owner management is local-only in v0.1.0. The Relay must remain independent of the full fleet control plane and PostgreSQL.

**Network constraint:** an ordinary unrooted Android app cannot bind TCP ports below 1024. The native listener therefore uses configurable unprivileged ports, defaulting to HTTP 8082 and HTTPS 8444. This avoids a confirmed collision with the existing website engine's default port 8080 and embedded Python's default port 8081. Conventional public HTTP(S) requires an owner-controlled router, firewall or reverse proxy to forward public 80 to 8082 and public 443 to 8444. Relay must validate port non-overlap at startup. A public IPv4 path with forwarding or a globally routed IPv6 path with equivalent edge forwarding is required. CGNAT without global IPv6 or inbound forwarding cannot accept public visitors. Cloudflare Tunnel remains a separate optional fallback.

**Release placement:** the native Android Relay service, JNI libraries for all declared ABIs, host pairing, independent credentials, durable route state, TLS, lifecycle/recovery and external-network acceptance are v0.1.0 requirements. Linux-native packaging remains v0.1.1. Multi-node scheduling, regional scale and advanced failover remain v0.1.2.

**Implementation status:** this is an approved architecture decision, not a claim of implementation. At decision time, `relay-node/` contains only a Cargo manifest; the native gateway engine, JNI bridge, Kotlin foreground service, node-side credential issuer, durable node state and physical/public acceptance remain to be implemented. See [Native Android Relay Architecture](NATIVE_ANDROID_RELAY.md).
