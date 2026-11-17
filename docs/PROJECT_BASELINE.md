# Pocket Host — Project Baseline

**Baseline snapshot:** branch `Alpha`, source commit `4185ea0f5e258c0d4c024f79de0a59f4757af9af`, audited 2026-11-17.  
**Complete file-level record:** [REPOSITORY_BASELINE.md](REPOSITORY_BASELINE.md). That document lists every tracked file and directory, module boundaries, source status, exact limits and verification evidence.

## Product invariants

- Exactly two Android operating modes: **Server only** and **Command Center + Server**. Both retain website hosting.
- Static HTML/CSS/browser JavaScript hosting must not require Python, Node, the browser console, the fleet control plane or a Relay provider.
- Main Android app and optional runtime companion are separate installable APKs. Python and Node currently ship together in the companion.
- Cloudflare Tunnel is optional and third-party. AssayFlux Relay is intended to be owner-operated on the user's own reachable hardware; a paid VPS is not required, but a public IPv4/IPv6 ingress path is.
- No software can make a CGNAT-only device accept unsolicited public traffic without a publicly reachable gateway or external tunnel provider.
- Source presence, successful compilation and CI tests are not the same as physical-device or public-domain acceptance.
- Preserve Pocket Host Design Language V1; this audit does not authorize a redesign.

## Implementation inventory

| Component | Current source present | Actual status |
|---|---|---|
| Android main app | Kotlin/Compose screens, foreground hosting service, local API, pairing, diagnostics, themes, biometric gate, site/file screens | Implemented foundation; v0.1.0 device acceptance open |
| Rust website engine | Tokio/Hyper JNI static HTTP server, hostname routes, visitor access modes, range/MIME/SPA handling, rate and connection limits | Source and tests present; 50 MiB per-file serving cap; device acceptance open |
| Website importer | Folder/ZIP staging and validation | 500,000,000-byte ZIP and expanded-content caps; actual 500 MB boundary/low-storage/device tests open |
| Full-app backup | `ApplicationBackupManager.kt` | 50 MiB static site, 50 MiB Python, 100 MiB Node, 200 MiB total uncompressed, 220 MiB encrypted archive; cannot back up a site near importer max |
| Workbench | Private draft workspace, nested explorer, basic editor, save, file/folder operations, change digests, local commit snapshots | Initial foundation exists; isolated preview, TypeScript build, owner-approved publish and rollback incomplete |
| Cloudflare Tunnel | Android token store, cloudflared process, readiness probe, reconnect supervisor, Settings controls, build script | Connector source/build present; public hostname/DNS/TLS acceptance open |
| AssayFlux Relay client | Android outbound SSE request/response agent | Coupled to RemoteControlAgent endpoint/device token; no independent Relay settings/credentials |
| AssayFlux Relay gateway | Rust visitor handler, DNS TXT proof, hostname routes, separate visitor listener, Caddy TLS | Exists inside full control-plane process; PostgreSQL and fleet credentials required; not standalone |
| Relay Node for Termux/Linux | No package, installer or standalone service in snapshot | Not implemented; mandatory v0.1.0 target |
| Runtime companion | Separate APK with CPython 3.11/Chaquopy and Node.js mobile 24.22.1/JNI | Both in same companion APK, in progress; not hardened sandbox |
| Runtime Library | 20 catalog JSON manifests | Python/Node in progress; remaining 18 planned metadata only |
| Browser console | React 18.3.1, TypeScript 5.7.2, Vite 6.0.5; remote control and Relay hostname panel | Separate browser app; build passes; not an Android runtime |
| Android device trust | P-256 identity, challenge/proof and grant foundations | In progress; v0.2.0 acceptance |
| Linux agent | Python package, P-256 identity, HTTPS polling, SQLite job store, typed jobs | Preview; v0.2.0 acceptance |
| Control plane | Rust/Axum, PostgreSQL migrations, admin sessions/passkeys, device commands, audit, Relay visitor listener | Source/tests present; combined fleet/Relay executable; full fleet acceptance open |
| Protocol | Protocol 1.0 schemas, default-deny role/job policies and validators | Contract tests pass; device-fabric integration incomplete |

## Critical limits and discrepancies

- Import limit: 500,000,000 bytes for ZIP input and independently for expanded site contents; 5,000 files, 10,000 entries, depth 32.
- Rust serving limit: 50 MiB per individual file; independent of total import size.
- Backup limit: 50 MiB static site, 50 MiB Python, 100 MiB Node, 200 MiB total uncompressed, 220 MiB encrypted archive.
- Relay current body limit: 8 MiB; gateway timeout 45 seconds; pending requests 32 global/8 per device; HTTP methods only, not arbitrary TCP/WebSocket.
- Current Relay HTTP listener redirects port 80 to HTTPS; there is no configurable plain-HTTP content mode.
- Main Android Network UI says “No built-in visitor password yet,” but the Rust engine already implements private/restricted Basic visitor authentication. The UI copy is stale.
- Main app has optional biometric app unlock and local pairing/approval; it does not currently have a conventional username/password mobile login page. Owner/passkey login is in the browser console.
- No standalone Termux Relay Node, no production deployment, no signed release, no APK artifact and no physical-device/public-domain acceptance are evidenced at this baseline.

## Public hosting direction without a VPS

v0.1.0 must deliver a standalone AssayFlux Relay Node installable on Termux/Android and Linux, independent of the fleet control plane, PostgreSQL and Docker. The user can host it on their own spare phone or home machine **only if** the network provides public IPv4 with port forwarding or globally routed IPv6 and firewall access for the public listener. A device behind CGNAT without global IPv6 cannot be the public gateway. The Android website host itself can remain behind CGNAT because it connects outbound to the Relay Node.

Cloudflare Tunnel remains an optional fallback, not a dependency of the first-party Relay.

## Build and verification evidence

- [Quality workflow — success](https://github.com/AssayFlux/PocketHost/actions/runs/44082938152) on baseline source commit: Rust tests, PostgreSQL/control-plane tests and migrations, Caddy/Compose validation, Python/Linux agent tests, Node metrics smoke test, web-console TypeScript/Vite build and Gradle wrapper validation passed.
- [Protocol contract workflow — success](https://github.com/AssayFlux/PocketHost/actions/runs/44082937765) on baseline source commit.
- [Android integration workflow — success](https://github.com/AssayFlux/PocketHost/actions/runs/44079487947) on code commit `5d90c1d`: cloudflared Android builds/ELF checks and Kotlin/instrumentation-source compilation passed. APK packaging and emulator jobs were skipped.
- No main APK build, signed release, physical-device test, live Cloudflare hostname test, standalone Relay Node deployment or external public-domain acceptance is recorded.

See [REPOSITORY_BASELINE.md](REPOSITORY_BASELINE.md) for the complete 252-file/71-directory inventory and detailed evidence.
