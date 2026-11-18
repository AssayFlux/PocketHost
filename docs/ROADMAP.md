# Pocket Host — Three-Version Roadmap

This roadmap replaces the previous five-step delivery sequence. It defines the first three release milestones and is intentionally expandable.

## v0.1.0 — First Hosting

**User outcome:** import a website into Pocket Host and serve it from an Android phone locally, on LAN and worldwide through the owner's domain.

### Deliverables
- Main Android app and bundled Rust static HTTP server.
- Folder and ZIP website import.
- 500,000,000-byte maximum ZIP archive and 500,000,000-byte maximum extracted website contents.
- Maximum 5,000 files, 10,000 total entries and 32 directory levels.
- Safe path validation, symlink rejection where applicable, sensitive-file exclusions and staging before activation.
- HTML, CSS, browser JavaScript, images, fonts and static MIME handling.
- Local phone preview and configurable port.
- Optional LAN binding and access from a second device on the same Wi-Fi.
- Cloudflare Tunnel public ingress for an owner-configured custom hostname, using an encrypted tunnel token and an outbound-only Android connector.
- First-party AssayFlux Relay implemented inside the Pocket Host Android APK as a Kotlin foreground service plus Rust/Tokio JNI engine. It supports inbound public visitor HTTP(S) and outbound authenticated host sessions, DNS TXT hostname proof, independent credentials, durable route state, TLS and visitor-only forwarding. No terminal app, Node.js, Docker or PostgreSQL dependency is permitted. Public access requires IPv4/IPv6 reachability and edge forwarding to unprivileged app listener ports; Linux-native packaging is deferred to v0.1.1.
- Minimal owner-facing Relay setup, pairing, hostname binding, DNS TXT challenge, TLS status, start/stop, health, recovery and route revocation controls in the Android app. The Relay is a separately managed service component, not a separate terminal-managed installation or a fleet-control-plane dependency. Linux-native Relay packaging remains deferred to v0.1.1; the full fleet operator console remains v0.1.2.
- Public HTTP and HTTPS for verified hostnames; valid HTTPS at the edge and HTTP-to-HTTPS redirect by default. Plain HTTP content serving is an explicit owner opt-in target; current Relay edge only redirects HTTP and has no plain-HTTP opt-in.
- Independent provider controls, health, reconnect, diagnostics and manual provider failover.
- End-to-end external-network verification of DNS, certificates, host routing, website assets and management-route isolation for Cloudflare and the native Android Relay service.
- Foreground service, persistent notification, start/stop/restart and Android lifecycle behavior.
- Clear permission, network, battery and thermal-state handling.
- Existing encrypted full-app backup/restore, interrupted-activation recovery and basic diagnostics. Current full-app backup limits are 500,000,000 bytes for the static site tree, 650,000,000 bytes total uncompressed and 680,000,000 bytes encrypted; Python and Node roots are separately capped at 50 MiB and 100 MiB. These source values do not indicate a static-site backup/import mismatch.
- Main APK build, install/upgrade and physical Android acceptance.

### Explicit exclusions
Full Workbench, built-in TypeScript compilation, React/Vite on-device builds, Python/Node server execution, worker enrollment, distributed roles, multi-host Relay scaling and advanced fleet orchestration are not v0.1.0 release blockers. Public Cloudflare Tunnel and single-host AssayFlux Relay acceptance ARE v0.1.0 release blockers.

### Hosting acceptance journey
1. Install the main APK on a named Android device.
2. Import a folder website and a ZIP website.
3. Verify 500 MB boundaries and reject oversized, malformed and unsafe archives.
4. Start the foreground host and open the site on the phone.
5. Connect a second device to the same Wi-Fi and open the LAN URL.
6. Verify navigation, CSS, browser JavaScript, images, refresh and MIME types.
7. Stop/restart hosting; background and foreground the app; test network change and process/device restart.
8. Create and restore an encrypted website backup.
9. Verify the custom domain from an independent network over HTTP and HTTPS through each configured provider; record DNS/TLS, response headers, content, provider status and management-route isolation.
10. Record the source SHA, main APK SHA-256 (including Relay native libraries), public network topology, device/Android version, logs and G0–G9 results.

## v0.1.1 — Workbench, Runtime & Linux Relay Installation

**User outcome:** develop and publish websites safely inside Pocket Host, and run optional server-side Python/Node projects.

### Deliverables
- Full Workbench Explorer and website project operations.
- Editor tabs, unsaved-change protection, file creation/rename/move/delete and search.
- Separate private draft workspace and immutable active production release.
- Isolated preview and accurate change summaries/per-file diffs.
- Durable local commit snapshots and deployment/release history.
- Bundled TypeScript compiler that emits browser JavaScript without requiring Node/npm for the basic compiler path.
- Bounded build/validation, actionable diagnostics and artifact digests.
- Explicit owner-approved staged publishing, atomic activation and rollback.
- Complete existing Python 3.11 and Node.js 24.22.1 companion integration, with install/IPC/lifecycle/health/recovery acceptance.
- Optional runtime packaging and isolation hardening; static hosting must remain independent.
- Linux-native Relay Node packaging, installation and service lifecycle tooling, with a documented supported-distribution/architecture matrix and install/upgrade/uninstall verification.
- Large workspace, multi-site, backup, storage-pressure and crash-recovery tests.

### Explicit exclusions
Production device-fabric support, arbitrary untrusted worker code execution and the full 20-language Runtime Library are not delivered by this version.

## v0.1.2 — Connected Device Fabric

**User outcome:** connect separately installed devices, assign bounded roles and operate a resilient personal device fabric.

### Deliverables
- Device identity, pairing, proof of possession, owner approval, grants and revocation.
- Authenticated transports and versioned protocol compatibility.
- First production worker agent on named OS/architecture/hardware.
- Bounded storage/backup, traffic, build/publish preparation and native-job workers.
- Durable job queue, idempotency, resource budgets, audit, health and signed updates.
- Optional Rust control plane and browser operator console.
- Multi-host Relay scheduling, regional gateway scaling, automated provider failover and traffic distribution beyond the v0.1.0 single-host public-ingress baseline.
- Visitor-route separation from management/runtime/MCU controls.
- Phone, worker, control-plane, network and provider outage/recovery drills.
- Release provenance, SBOM and support matrix for every shipped package.

## Later expansion

After the first three releases, the roadmap may expand to the independently downloadable 20-language Runtime Library, iOS, Windows/macOS agents, additional Linux/SBC profiles, DemonCore/MCU adapters, distributed storage, traffic scaling and advanced scheduling. These are not silently included in the first three releases.

## Release governance

Every version must document and pass G0–G9. The gate definitions and acceptance matrix in [RELEASE_GATES.md](RELEASE_GATES.md) are mandatory. A planned feature is not a supported feature; source presence is not acceptance.
