# Pocket Host — Project Baseline

**Current source snapshot:** branch `Alpha`, commit `9debd9b630d460c5feed92d07b3c20da319a9e4d`, checked 2026-11-18. The complete recursive Git tree contains 260 tracked files across 72 directories. The detailed 252-file path inventory below originated at `4185ea0` and is historical; see [REPOSITORY_BASELINE.md](REPOSITORY_BASELINE.md) for the later snapshot deltas and exact commit identities. Audit findings remain in [REMEDIATION_AUDIT.md](REMEDIATION_AUDIT.md).  
**Complete file-level record:** [REPOSITORY_BASELINE.md](REPOSITORY_BASELINE.md). That document lists every tracked file and directory, module boundaries, source status, exact limits and verification evidence.

## Product invariants

- Exactly two Android operating modes: **Server only** and **Command Center + Server**. Both retain website hosting.
- Static HTML/CSS/browser JavaScript hosting must not require Python, Node, the browser console, the fleet control plane or a Relay provider.
- Main Android app and optional runtime companion are separate installable APKs. Python and Node currently ship together in the companion.
- Cloudflare Tunnel is optional and third-party. AssayFlux Relay is an owner-operated native Android service bundled in the Pocket Host APK; a paid VPS is not required, but a public IPv4/IPv6 ingress path and edge forwarding to unprivileged listener ports are required.
- No software can make a CGNAT-only device accept unsolicited public traffic without a publicly reachable gateway or external tunnel provider.
- Source presence, successful compilation and CI tests are not the same as physical-device or public-domain acceptance.
- Preserve Pocket Host Design Language V1; this audit does not authorize a redesign.

## Implementation inventory

| Component | Current source present | Actual status |
|---|---|---|
| Android main app | Kotlin/Compose screens, foreground hosting service, local API, pairing, diagnostics, themes, biometric gate, site/file screens | Implemented foundation; v0.1.0 device acceptance open |
| Rust website engine | Tokio/Hyper JNI static HTTP server, hostname routes, visitor access modes, range/MIME/SPA handling, rate and connection limits | Source and tests present; 500,000,000-byte (500 MB decimal) per-file serving cap; device acceptance open |
| Website importer | Folder/ZIP staging and validation | 500,000,000-byte ZIP and expanded-content caps; actual 500 MB boundary/low-storage/device tests open |
| Full-app backup | `ApplicationBackupManager.kt` | 500,000,000-byte static site, 50 MiB Python, 100 MiB Node, 650,000,000-byte total uncompressed, 680,000,000-byte encrypted archive; static-site limit matches importer expanded-content cap |
| Workbench | Private draft workspace, nested explorer, basic editor, save, file/folder operations, change digests, local commit snapshots | Initial foundation exists; isolated preview, TypeScript build, owner-approved publish and rollback incomplete |
| Cloudflare Tunnel | Android token store, cloudflared process, readiness probe, reconnect supervisor, Settings controls, build script | Connector source/build present; public hostname/DNS/TLS acceptance open |
| AssayFlux Relay client | Android outbound SSE request/response agent, dedicated Android Keystore-backed `RelayCredentialStore`, independent Network settings controls | Android-side configuration and credentials are independent of RemoteControlAgent; node-side credential issuance and live public acceptance remain open |
| AssayFlux Relay gateway | Rust visitor handler, DNS TXT proof, hostname routes, separate visitor listener, Caddy TLS | Exists inside full control-plane process; PostgreSQL and fleet credentials required; not standalone |
| Native Android Relay Node | `relay-node/Cargo.toml` package manifest only | Scaffold only: no Rust engine source, JNI bridge, Android foreground service, inbound listener, host credential issuer or native Android lifecycle; not runnable and not release-ready. v0.1.0 target is integration into the main Pocket Host APK; Linux-native packaging remains v0.1.1 |
| Runtime companion | Separate APK with CPython 3.11/Chaquopy and Node.js mobile 24.22.1/JNI | Both in same companion APK, in progress; not hardened sandbox |
| Runtime Library | 20 catalog JSON manifests | Python/Node in progress; remaining 18 planned metadata only |
| Browser console | React 18.3.1, TypeScript 5.7.2, Vite 6.0.5; remote control and Relay hostname panel | Separate browser app; build passes; not an Android runtime |
| Android device trust | P-256 identity, challenge/proof and grant foundations | In progress; v0.1.2 acceptance |
| Linux agent | Python package, P-256 identity, HTTPS polling, SQLite job store, typed jobs | Preview; v0.1.2 acceptance |
| Control plane | Rust/Axum, PostgreSQL migrations, admin sessions/passkeys, device commands, audit, Relay visitor listener | Source/tests present; combined fleet/Relay executable; full fleet acceptance open |
| Protocol | Protocol 1.0 schemas, default-deny role/job policies and validators | Contract tests pass; device-fabric integration incomplete |

## Critical limits and discrepancies

- Import limit: 500,000,000 bytes for ZIP input and independently for expanded site contents; 5,000 files, 10,000 entries, depth 32.
- Rust serving limit: 500,000,000 bytes (500 MB decimal) per individual file; independent of total import size. Manual single-file import is 50 MiB; text editing is 256 KiB.
- Backup limits: 500,000,000-byte static site, 50 MiB Python, 100 MiB Node, 650,000,000-byte total uncompressed, 680,000,000-byte encrypted archive. Near-limit physical-device and interruption tests remain open.
- Relay current body limit: 8 MiB; gateway timeout 45 seconds; pending requests 32 global/8 per device; HTTP methods only, not arbitrary TCP/WebSocket.
- Current Relay HTTP listener redirects port 80 to HTTPS; there is no configurable plain-HTTP content mode.
- Main Android Network UI says “No built-in visitor password yet,” but the Rust engine already implements private/restricted Basic visitor authentication. The UI copy is stale.
- Main app has optional biometric app unlock and local pairing/approval; it does not currently have a conventional username/password mobile login page. Owner/passkey login is in the browser console.
- The current tree has a Relay Node Cargo manifest scaffold only; no native gateway engine, JNI bridge, Android foreground service or node-side credential issuer is present. No production deployment, signed release, APK artifact or physical-device/public-domain acceptance is evidenced.

## Public hosting direction without a VPS

v0.1.0 must deliver the native Android Relay service inside the main Pocket Host APK: Kotlin foreground service + Rust/Tokio engine through JNI, independent of the fleet control plane, PostgreSQL and Docker. The owner can enable it on an Android device **only if** the network provides public IPv4 with forwarding or globally routed IPv6 plus edge forwarding/proxying to the app's unprivileged listener ports. A device behind CGNAT without global IPv6 or inbound forwarding cannot be the public gateway. A separate website-host phone can remain behind CGNAT because it connects outbound to the Relay.

Cloudflare Tunnel remains an optional fallback, not a dependency of the first-party Relay.

## Build and verification evidence

- [Quality workflow — success](https://github.com/AssayFlux/PocketHost/actions/runs/44091819401) on historical source commit `0784b3294f2de9f435479c7a290f336e85b78df0`: Rust tests, PostgreSQL/control-plane tests and migrations, Caddy/Compose validation, Python/Linux agent tests, Node metrics smoke test, web-console TypeScript/Vite build and Gradle wrapper validation passed.
- [Protocol contract workflow — success](https://github.com/AssayFlux/PocketHost/actions/runs/44091819294) on historical source commit `0784b3294f2de9f435479c7a290f336e85b78df0`.
- [Android integration workflow — success](https://github.com/AssayFlux/PocketHost/actions/runs/44079487947) on code commit `5d90c1d`: cloudflared Android builds/ELF checks and Kotlin/instrumentation-source compilation passed. APK packaging and emulator jobs were skipped.
- No signed release APK, physical-device Relay-service test, live Cloudflare hostname test, native Relay public-domain deployment or external public-domain acceptance is recorded.

See [REPOSITORY_BASELINE.md](REPOSITORY_BASELINE.md) for the historical file-level inventory and current-tree delta.


**Remediation:** see [Remediation Audit](REMEDIATION_AUDIT.md) for changes and pending verification. Historical CI links above are evidence for the historical commit only, not the remediation branch.
