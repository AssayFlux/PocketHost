# AssayFlux PocketHost — Master Engineering Plan

**Document status:** Baseline product and architecture plan  
**Plan version:** 1.0  
**Date:** 2026-11-14  
**Owner:** AssayFlux  
**Brand:** Designed by AssayFlux

> This document defines intended architecture and acceptance gates. It does not claim that planned integrations have already been implemented or validated.

## 1. Product definition

AssayFlux PocketHost is a standalone native Android application for importing, deploying and hosting websites from the phone. The initial target is the user's Vivo Y95. The application must not depend on Termux.

The first usable product is static hosting with local preview, lifecycle controls and safe deployments. Public hosting is enabled only after an Android-compatible Cloudflare Tunnel connector is proven. Python and Node.js are optional later-stage runtimes.

### Primary outcomes
- Guided import and deployment of an existing website.
- Native start/stop/status controls.
- Local network preview.
- Public domain access through a verified tunnel.
- Safe updates, backups and rollback.
- Observable resource and service health.
- Optional multi-site and backend runtime support.

### Operating modes
- **Simple Mode (default):** guided import, domain/tunnel setup, publish, status and rollback.
- **Advanced Mode:** host/port mapping, runtime settings, routing, request policies, resource limits, logs and diagnostics.

Both modes use the same engine and configuration model. Advanced Mode reveals controls; it does not create a second implementation.

## 2. Product design and UX

### Visual system
Use the established AssayFlux palette:
- Deep teal: `#24434A`
- Warm bronze: `#B18B5E`
- Soft ivory: `#F6F4EF`
- White: `#FFFFFF`
- Charcoal: `#1D292B`
- Slate: `#657174`

Design direction: modern, calm, precise and editorial; restrained rounded surfaces; clear hierarchy; accessible contrast; consistent line icons; generous spacing; responsive layouts; light and dark themes. Do not introduce an unrelated palette.

### Navigation
1. Home / Dashboard
2. Websites
3. Tunnel
4. Traffic / Analytics
5. Settings

Secondary destinations: site details, runtimes, logs, security, backups, diagnostics and onboarding.

### Dashboard information
- Overall hosting state and explanatory status
- Active website count
- Tunnel connectivity and public hostname
- Request count, bandwidth and error count
- CPU/memory/storage indicators where reliable
- Start, stop, restart and local preview actions
- Recent events and actionable warnings

### Explicit service states
`Stopped`, `Starting`, `Local hosting`, `Connecting tunnel`, `Public hosting`, `Degraded`, `Recovering`, `Error`.

A green indicator alone is insufficient. Every degraded/error state must include a cause when known and a safe next action.

### Core user journeys
**First publish:** onboarding → device check → import folder/ZIP → validation → local preview → tunnel/domain configuration → external verification → publish.

**Update:** import new release → validate → preview → activate atomically → monitor → rollback if needed.

**Troubleshoot:** inspect status → identify layer (Android/service/engine/tunnel/domain/runtime) → view redacted diagnostics → apply guided recovery.

## 3. Technology decisions

| Layer | Technology | Ownership |
|---|---|---|
| Android app | Kotlin + Jetpack Compose | UI, navigation and Android APIs |
| Background operation | Android Foreground Service | User-visible hosting lifecycle |
| Central engine | Rust | HTTP, routing, proxy, policies and metrics |
| Async runtime | Tokio | Engine concurrency |
| HTTP stack | Hyper + Tower | HTTP server/client and middleware |
| Kotlin/Rust boundary | JNI with versioned DTOs/events | Coarse-grained control and status |
| Browser console | React + TypeScript + Vite | Optional remote/local management UI |
| Web styling | CSS and shared design tokens | Console presentation |
| Python runtime | Android-compatible Python integration; evaluate Chaquopy | Optional Python applications |
| Node runtime | Verified Android-compatible Node distribution | Optional Node applications |
| Persistence | SQLite + Room | Metadata and configuration |
| Preferences | DataStore | Android user settings |
| Secret protection | Android Keystore-backed encryption | Tunnel and application secrets |

### Language rules
- Rust is the central hosting engine language.
- Kotlin is the native Android language.
- TypeScript replaces JavaScript as the authored language for the web console and web tooling. Browser execution still uses compiled JavaScript output.
- Python is used only for user-selected Python backend applications and their integration.
- Node.js is an optional runtime for applications; it is not the core engine.
- SQL is used for database queries and schema work.

Do not create competing Kotlin and Rust HTTP servers. Rust owns the production HTTP request path. Kotlin may use Android networking libraries for app-specific network operations, but not as a second hosting engine.

### Bridge rules
- JNI calls are coarse-grained: initialize, start, stop, configure, deploy, query status.
- Do not cross JNI for every HTTP request.
- Use typed, versioned request/response DTOs and structured events.
- Define error codes and bridge compatibility explicitly.
- Keep Android lifecycle ownership in Kotlin; Rust must support deterministic shutdown.

## 4. System architecture

### Request path
Visitor → Cloudflare Edge (DNS/HTTPS/security) → outbound encrypted tunnel → local Rust listener/router → selected static site or optional backend → response through tunnel.

The origin listener should bind only to loopback for tunnel-to-origin traffic. A separate, explicit LAN preview listener may be enabled with user consent and clear network exposure warnings. The administration API must never be routed as a public website.

### Rust crate boundaries
- `pockethost-core`: lifecycle, configuration, orchestration and public engine API.
- `pockethost-http`: HTTP listeners, protocol handling and response construction.
- `pockethost-router`: hostname/path matching and route resolution.
- `pockethost-static`: secure static file delivery, MIME types, caching and index fallback.
- `pockethost-proxy`: reverse proxy to optional local runtimes.
- `pockethost-security`: request validation, access policy and rate limiting.
- `pockethost-storage`: site metadata, release activation and filesystem operations.
- `pockethost-tunnel`: connector lifecycle abstraction and health.
- `pockethost-runtime`: runtime adapters and process supervision.
- `pockethost-monitoring`: health, metrics and bounded event collection.

Avoid splitting into tiny crates without a real dependency or ownership boundary. The list is a target decomposition, not a requirement to create empty crates immediately.

### Android module boundaries
- UI/navigation and design system
- Hosting foreground service and notification
- Boot/restart preference handling (subject to Android restrictions)
- Storage Access Framework import
- Kotlin/Rust bridge
- Tunnel credential setup
- Database/repositories
- Device/resource monitoring
- Backup/export and diagnostics

## 5. Website import and deployment

### Import sources
- Folder selection through Android Storage Access Framework.
- ZIP archive selection.

### Validation
- Require a usable entry point for static sites (configurable index document).
- Validate file count, total size and supported paths.
- Reject path traversal, absolute paths, symlinks where unsafe, and archive extraction escapes.
- Block or quarantine sensitive/internal files such as `.env`, `.git`, private keys and PocketHost metadata.
- Do not execute imported content during validation.
- Present clear validation results before activation.

### Versioned deployment model
Each site has immutable release directories and metadata. Import into a staging directory, validate, then activate a release using an atomic and filesystem-appropriate pointer/metadata switch. Never overwrite the active release in place.

A failed import/deployment must leave the current release serving. Keep configurable retention for old releases and backups.

### Site management
- Create, rename, enable and disable sites.
- Assign hostnames.
- Preview locally.
- Inspect deployment history.
- Roll back to a previous release.
- Export/restore site backups.
- View storage consumption.
- Delete with confirmation and clear impact summary.

### Suggested private storage layout
```text
files/
  sites/<site-id>/
    releases/<release-id>/
    staging/
    backups/
    site-metadata.json
  database/
  runtime-data/
  logs/
  temporary/
```
Use app-private storage by default. Avoid assuming symbolic-link support; use a robust active-release abstraction.

## 6. Tunnel and domain strategy

Cloudflare Tunnel is the chosen public connectivity path because it uses outbound connectivity and can work around common inbound NAT/CGNAT restrictions. Cloudflare still provides edge infrastructure; PocketHost does not eliminate all third-party infrastructure.

### Mandatory Android feasibility gate
Cloudflare's standard Linux ARM64 build must not be assumed to run on Android. Prove an Android-compatible connector on the exact target device before implementing public hosting as a committed capability.

Prototype requirements:
1. Build or obtain a legally and technically suitable Android ARM64 connector.
2. Start/stop it from the native app without Termux or root.
3. Authenticate a named tunnel without plaintext credential storage.
4. Route tunnel traffic to a loopback Rust test server.
5. Serve a public test hostname from a separate network.
6. Recover after Wi-Fi/mobile network changes.
7. Report connector failures and exit state reliably.
8. Stop cleanly with the hosting service.

Quick/ephemeral tunnels are not the production domain strategy. Production uses a named tunnel and configured hostname/DNS. Do not invent fallback connectivity or show Public Hosting until externally verified.

### Domain management
- Hostname-to-site mapping.
- Domain ownership/configuration guidance.
- TLS/HTTPS state from the edge.
- Clear DNS/tunnel diagnostics.
- Multiple hostnames per site only after basic mapping is reliable.

## 7. Runtime strategy

### Static runtime — first
Must support HTML, CSS, JavaScript assets supplied by the website, images, fonts, downloads, MIME types, caching headers, safe index fallback and configurable 404 behavior. The static runtime must not require Node.js.

### Python — later optional module
Evaluate Android packaging and ABI support, Python version, native wheels, Flask compatibility, WSGI server availability, startup time, memory and lifecycle control. Do not promise arbitrary PyPI package compatibility. Run only trusted code approved by the owner.

### Node.js — later optional module
Prove a genuine Android-compatible Node runtime for the target ABI and Android version. Validate process lifecycle, native modules, filesystem permissions, sockets, memory and TypeScript build output. Ordinary Linux Node binaries are not automatically Android-compatible.

### Runtime policy
- Runtime modules are optional and loaded only when selected.
- Static hosting remains available without Python or Node.
- Initial device target should run at most one optional application runtime at a time until resource tests justify more.
- No public code upload or automatic execution.
- Environment secrets are stored separately from site files and redacted from logs.

## 8. Security requirements

1. Admin API separate from public request routing.
2. Remote administration disabled by default.
3. Local admin authentication and session protection.
4. Optional Cloudflare Access integration only after explicit configuration and testing.
5. Keystore-backed encryption for tunnel tokens and secrets.
6. Strict canonical path validation and per-site filesystem boundaries.
7. Directory listing disabled by default.
8. Public uploads disabled by default.
9. Request method, header, body-size, timeout and connection limits.
10. Per-IP/route rate limits where trustworthy client identity is available.
11. Trusted proxy/client-IP handling; never blindly trust visitor-supplied forwarding headers.
12. Redacted, bounded, rotating logs.
13. No secrets in source control, crash reports or diagnostics exports.
14. Runtime code is trusted-only and explicitly enabled.
15. Signed release APKs and published checksums.
16. Backup export warns about secret handling and supports encrypted backup where feasible.

Python and Node applications execute under the Android app's OS identity unless a real isolation boundary is implemented. Do not call them sandboxed merely because they are separate processes.

## 9. Reliability and device constraints

The exact Vivo Y95 variant, Android version, ABI, memory and storage must be recorded before finalizing minSdk/NDK/ABI decisions. A likely configuration is not a verified configuration.

### Resource controls
- Bounded worker/thread counts.
- Bounded concurrent connections.
- Request timeouts and maximum body sizes.
- Bounded in-memory caches.
- Log rotation and retention.
- Per-runtime memory/CPU observation where available.
- Storage thresholds and deployment retention.
- Lazy runtime loading.
- Avoid permanent wake locks.
- Thermal and battery awareness.

### Android lifecycle
Use a correctly declared foreground service for user-visible hosting. Follow Android-version-specific foreground-service and notification rules. Support graceful stop, process death recovery and network reconnection where possible. Boot auto-start is opt-in and must follow Android background-start restrictions.

A foreground service improves continuity but cannot guarantee 24/7 availability. Android vendors may apply additional battery restrictions. Provide an honest uptime and battery warning.

## 10. Observability and diagnostics

### Metrics
- Requests per interval
- Response status classes
- Bytes served
- Approximate bandwidth
- Response latency
- Active connections
- Tunnel state and reconnect count
- Runtime health
- Storage consumption
- App/engine memory and CPU where measurable
- Thermal/battery state where available

### Logs
Structured levels: DEBUG, INFO, WARN, ERROR. Provide filtering by site, subsystem, severity and time. Rotate logs and redact tokens, cookies, authorization headers, secrets and sensitive form data.

Diagnostics export must be user-triggered and previewable before sharing.

## 11. Repository structure target

```text
PocketHost/
├── app-android/
│   └── app/
├── android/
│   ├── ui/
│   ├── service/
│   ├── permissions/
│   ├── lifecycle/
│   ├── notifications/
│   ├── data/
│   └── bridge/
├── rust-engine/
│   └── crates/
│       ├── pockethost-core/
│       ├── pockethost-http/
│       ├── pockethost-router/
│       ├── pockethost-static/
│       ├── pockethost-proxy/
│       ├── pockethost-security/
│       ├── pockethost-storage/
│       ├── pockethost-tunnel/
│       ├── pockethost-runtime/
│       └── pockethost-monitoring/
├── web-console/
│   ├── src/
│   ├── public/
│   ├── package.json
│   └── tsconfig.json
├── runtimes/
│   ├── python/
│   └── node/
├── shared/
│   ├── api-contracts/
│   └── schemas/
├── tests/
│   ├── rust/
│   ├── android/
│   ├── integration/
│   ├── security/
│   └── device/
├── assets/
├── docs/
├── .github/workflows/
├── README.md
├── LICENSE
└── CONTRIBUTING.md
```

Create directories when their implementation begins; avoid committing placeholder modules that imply unsupported functionality.

## 12. Phased roadmap and exit criteria

### Phase 0 — Feasibility (first)
- Confirm device model, Android version, ABI, RAM and available storage.
- Compile Rust for the verified Android target.
- Load Rust library from Kotlin/JNI.
- Start a loopback HTTP server and serve a test page.
- Measure startup, idle memory, CPU and shutdown.
- Prototype Android-compatible tunnel connector.

**Exit:** local Rust server works under app control on the target device; tunnel feasibility is documented with a real external-network test or a clear blocker.

### Phase 1 — Native foundation
- Kotlin/Compose app shell and design system.
- Onboarding and device diagnostics.
- Foreground service, notification and state model.
- Persistent settings and database.
- Versioned Rust bridge.

**Exit:** app installs on target device; start/stop state is reliable; service behavior and restrictions are documented.

### Phase 2 — Static engine
- Rust HTTP server, static delivery and routing.
- Secure path handling, MIME and caching.
- Bounded requests and structured logs.
- Local preview and LAN preview with explicit consent.

**Exit:** a second device on the same Wi-Fi can access an imported test website; security tests reject traversal and sensitive-file access.

### Phase 3 — Website lifecycle
- Folder/ZIP import.
- Validation and staging.
- Versioned deployments.
- Rollback, backup and restore.
- Multiple site metadata and hostname assignment.

**Exit:** user can import, deploy, update and roll back without destroying the active release.

### Phase 4 — Public hosting
- Named tunnel integration.
- Credential protection.
- Domain mapping and health state.
- Network-change recovery.
- External availability checks.

**Exit:** configured public hostname serves the site from a separate network; otherwise public hosting remains explicitly unsupported.

### Phase 5 — TypeScript console
- React + TypeScript + Vite.
- Typed management API.
- Responsive site/deployment/metrics/log views.
- Authentication and session security.
- Remote admin remains disabled until explicitly secured.

**Exit:** authenticated console can manage permitted operations without exposing admin routes through public site traffic.

### Phase 6 — Python runtime
- Android packaging compatibility.
- Flask/WSGI proof.
- Backend routing and lifecycle.
- Runtime logs, configuration and resource controls.

**Exit:** trusted Python demo app works locally and, if tunnel phase passed, through the public hostname.

### Phase 7 — Node runtime
- Android Node build and ABI validation.
- TypeScript compile/build workflow.
- Process supervision and routing.
- Native module compatibility review.

**Exit:** trusted Node demo works on the target device under documented resource limits.

### Phase 8 — Production hardening
- Crash/restart/network transition tests.
- Low-storage and thermal behavior.
- Security review and threat-model update.
- Upgrade, backup and restore tests.
- Release signing, checksums and documentation.

**Exit:** repeatable release process and documented known limitations; no unresolved critical security issue.

## 13. Quality gates

| Area | Acceptance |
|---|---|
| Local hosting | External LAN device can load the site |
| Public hosting | External mobile network can load configured domain |
| Deployment | Failed deploy preserves active release |
| Rollback | Previous release restores correctly |
| Security | Traversal, private-file and unauthorized-admin tests pass |
| Lifecycle | Start/stop/restart and process-death behavior verified |
| Network | Tunnel recovery or explicit failure after network transition |
| Storage | Low-storage handling does not corrupt active content |
| Thermal | App degrades safely and communicates device pressure |
| Runtime | Optional runtimes remain disabled unless installed/configured |
| Upgrade | Site data and settings survive app upgrade |

Tests must run at the appropriate levels: Rust unit/integration tests, Android instrumentation where supported, connector integration tests, security tests and real-device acceptance tests.

## 14. Release and versioning

- Initial development target: `0.1.0`.
- Keep app version, engine protocol version and management API version distinct.
- Use semantic versioning for the Rust core and public API contracts where practical.
- Sign release APKs; never commit signing keys.
- Publish SHA-256 checksums.
- Document device/ABI compatibility and known limitations.
- Do not advertise Python, Node or public tunnel features until their acceptance gates pass.

## 15. Immediate implementation backlog

1. Record verified Vivo Y95 hardware/software details.
2. Establish Gradle/NDK/Rust Android toolchain compatibility.
3. Create minimal Kotlin app shell and Rust JNI library.
4. Implement a local-only Rust static HTTP proof.
5. Add a minimal website import/preview experiment.
6. Prototype Android Cloudflare connector separately.
7. Record benchmark and feasibility results.
8. Only then expand into the full UI and deployment workflow.

## 16. Decision log

- Native Android app: Kotlin + Jetpack Compose.
- Central engine: Rust.
- Web console/tooling: TypeScript (not JavaScript-authored source).
- Static hosting is the first supported workload.
- Cloudflare Tunnel is the selected public connectivity strategy, pending Android proof.
- Python and Node are optional later runtimes.
- SQLite/Room is the local metadata store.
- App-private storage is the default site storage.
- Simple Mode is the default user experience.
- Advanced Mode exposes detailed operational controls.
- Main branch is `main`; do not rename it as part of this plan.
