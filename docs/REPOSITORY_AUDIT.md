# Pocket Host — Repository Deep Audit

**Audited source branch:** `Alpha`  
**Source snapshot:** `2087938a35dc88ed7588d97ffed8bd65fe8b40a9` (259 tracked files, 72 directories)  
**Remediation branch:** `audit/remediation-baseline-2026-11` (260 tracked blobs, 72 directories at baseline snapshot)  
**Audit date:** 2026-11-18  
**Complete inventory:** [REPOSITORY_BASELINE.md](REPOSITORY_BASELINE.md)

## 1. Executive finding

Pocket Host is not a blank scaffold. The repository contains a Kotlin/Compose Android app, Rust static HTTP engine, website importer and backup manager, initial Workbench, Python/Node runtime companion, Android device-trust foundation, Linux agent preview, Rust/Axum fleet control plane, browser operator console, Cloudflare Tunnel integration and a partial first-party Relay visitor path.

However, **the current Relay implementation is not yet the independent self-hosted Relay product the project needs**. The gateway remains coupled to the fleet control plane and PostgreSQL, and uses fleet device credentials. The Android host-side RelayAgent already has an independent RelayCredentialStore and lifecycle. There is no standalone Termux Relay Node executable or installer. The previous documentation described a VPS deployment as though it were the required product shape; that is corrected by this audit.

## 2. Product and UI boundaries confirmed

- Android stores exactly two operating modes: Server only and Command Center + Server.
- Both modes retain website hosting; the Devices view is conditional on Command Center + Server.
- Main app contains Home, Dashboard, Sites/file operations, Workbench, Learn and Settings.
- General settings contain System/Light/Dark appearance, named classic themes, optional biometric app unlock and auto-start preference.
- The biometric feature is a local app gate, not a mobile account login page. The browser console contains owner/admin session and passkey flows.
- Main Android app has Cloudflare named-tunnel controls, local hostname-to-site routing and independent Relay endpoint/credential controls in Network settings. Relay credentials use a dedicated Android Keystore-backed AES-GCM store.
- The LAN-access toggle warns that LAN access itself does not provide visitor authentication. Keep this warning unless end-to-end tests prove the selected LAN/IP route enforces the site's hostname-based private/restricted policy; do not imply that the UI toggle adds authentication.
- Main app is vertically scrollable in its root content column; physical Android font scale, keyboard, safe-area, navigation and service behavior still need named-device acceptance.

## 3. Website hosting review

### Android service

HostingService starts the foreground service, recovers interrupted site/backup operations, starts the local management API, starts configured remote control and Cloudflare supervision, and launches the Rust engine. Rust binds loopback by default; LAN access is explicit. The local site must continue without Cloudflare, Relay or fleet control.

### Importer and quotas

SiteImporter source/UI is set to 500,000,000 bytes for ZIP input and independently for expanded website contents. It also caps files at 5,000, total ZIP entries at 10,000 and depth at 32. Existing instrumentation coverage checks the configured constant and ZIP happy path; it does not prove a real 500 MB import. Boundary, low-storage, memory-pressure and physical-device tests remain open.

The Rust engine has a separate 500,000,000-byte (500 MB decimal) per-file response cap. Manual single-file import remains 50 MiB; text editing is 256 KiB.

### Backup capacity — source recheck

The active full-app backup implementation is `ApplicationBackupManager`, not the older `SiteBackupManager`. The current Alpha source now sets the static website root limit to 500,000,000 bytes, Python to 50 MiB, Node to 100 MiB, total uncompressed data to 650,000,000 bytes and the encrypted archive to 680,000,000 bytes. The static website backup ceiling therefore covers the importer's 500,000,000-byte expanded-content limit. The previous 50 MiB / 200 MiB figures in this audit were stale and must not be used for release decisions. Remaining acceptance: exercise near-limit backup/restore on real devices, verify free-space preflight and interruption recovery, and test combined optional runtime roots against their documented per-root and total caps.

### Static engine

Rust Tokio/Hyper serves HTTP, host-routed static sites, MIME types, SPA index fallback, byte ranges and visitor access modes. Private/restricted visitor Basic authentication and rate limiting are implemented. The main Android Settings copy claiming visitor passwords are absent is stale.

## 4. Public ingress review

### Cloudflare Tunnel

The Android source has an encrypted named-tunnel token store, cloudflared child-process runner, readiness probe, foreground-service supervisor and settings controls. The build helper compiles a pinned cloudflared version for arm64-v8a, armeabi-v7a and x86_64. The public hostname and origin route are configured manually by the owner in Cloudflare. No public DNS/TLS/content acceptance is recorded.

### AssayFlux Relay

Present source:
- Android RelayAgent uses outbound authenticated HTTPS/SSE and forwards bounded visitor HTTP requests to the local Rust website engine.
- Relay hostname registration and DNS TXT verification exist in the Rust/Axum service.
- The visitor listener is separate from the management listener; Caddy provides the public TLS edge.
- The web-console contains hostname registration, verification and deletion UI.

Critical coupling/gaps:
- Android RelayAgent uses its own RelayCredentialStore; it no longer requires fleet remote-control credentials. Node-side host credential issuance/pairing remains unimplemented.
- The gateway shares the same executable as the fleet control plane and requires PostgreSQL, DATABASE_URL, admin token and control hostname.
- Compose currently assumes Docker, PostgreSQL and Caddy.
- The in-memory RelayHub loses active sessions and pending requests on process restart.
- The current Caddy port-80 listener redirects to HTTPS; the Relay visitor handler requires a trusted HTTPS frontend. There is no plain-HTTP content opt-in.
- Android Network settings expose independent Relay endpoint and credential setup, but not standalone-node hostname/TXT/TLS provisioning.
- A `relay-node/Cargo.toml` package manifest has since been added, but there is still no `src/main.rs`, runnable gateway, installer, service unit, Termux lifecycle or public-network acceptance. It is a scaffold, not an implementation.

### Correct no-VPS product boundary

A paid VPS is not required. A separate owner-operated Relay Node can run on Termux/Android or Linux hardware the owner already owns. It still needs a publicly reachable network path (public IPv4 with router port forwarding, or global IPv6 with the firewall opened) and DNS A/AAAA records. If the only network is CGNAT with no global IPv6/port forwarding, the device cannot accept inbound visitors. Another owner-controlled reachable node or optional Cloudflare Tunnel is then required.

The current `relay-node/` directory contains only a Cargo manifest. The v0.1.0 implementation must add a runnable lightweight standalone Relay Node, independent host credentials, durable/recoverable host-session handling, Termux/Linux lifecycle support, TLS termination or a rigorously defined trusted edge, and tests proving visitor/admin isolation. It must not depend on PostgreSQL, Docker or fleet credentials.

## 5. Workbench review

WorkbenchWorkspaceManager and PocketHostWorkbenchScreen provide a private per-site draft workspace, nested explorer, basic editor, draft save, file/folder creation, rename/delete, change digest and local commit snapshots. Draft saves do not modify the live site.

Still incomplete: full editor/operation set, isolated preview, per-file diffs, TypeScript compilation, immutable release staging, owner-approved activation, rollback and Android acceptance. Workbench is an existing foundation, not a completed GitHub-equivalent workbench.

## 6. Runtime review

- Separate runtime-worker APK/application ID and UID exist.
- CPython 3.11 uses Chaquopy; Node.js mobile 24.22.1 uses JNI/libnode.
- Both are bundled together in one companion APK, not independent per-language packages.
- Node SDK/native inputs are downloaded at build time from a pinned release and checked by SHA-256.
- Python and Node source include project import, service lifecycle and metrics. The runtime catalog status remains in-progress.
- Python project code runs under the companion UID; Node runs in a separate process in that UID. Neither should be described as a hardened hostile-code multi-tenant sandbox.
- Eighteen other runtime catalog entries are metadata-only planned placeholders.

## 7. Device fabric and protocol review

Protocol 1.0 schemas, role/job policy, validators, Android P-256 trust foundations and a Linux Python agent preview exist. Linux agent has durable SQLite state, HTTPS polling and typed allow-listed job execution. Full end-to-end enrollment, credential delivery, revocation propagation, worker acceptance and additional OS packages remain incomplete. The full fleet control plane belongs to v0.2.0 and must not be required for local hosting or standalone Relay.

## 8. Build and workflow review

- Quality CI passed on the baseline source commit: Rust, control-plane/PostgreSQL/migrations, Caddy/Compose validation, Python/Linux agent tests, Node metrics smoke test, web-console build and Gradle wrapper validation.
- Protocol schema/policy CI passed on `0784b3294f2de9f435479c7a290f336e85b78df0` ([run 44091819294](https://github.com/AssayFlux/PocketHost/actions/runs/44091819294)).
- Android integration CI passed on code commit 5d90c1d for cloudflared Android native builds and Kotlin/instrumentation-source compilation.
- In that Android workflow, APK packaging and emulator instrumentation were skipped. Push CI compiles sources but does not itself prove a packaged APK or installed app.
- Signed release and control-plane deployment workflows are manual. The deployment workflow requires an SSH host and configured environment/secrets.
- No signed APK, physical-device acceptance, production control-plane deployment, public Cloudflare test or Relay public-domain test is recorded.

## 9. Priority correction list

1. Implement standalone owner-operated AssayFlux Relay Node for Termux/Android and Linux; no paid VPS, Docker or PostgreSQL prerequisite.
2. Implement standalone Relay Node host identity, credential issuance, hostname/TXT/TLS provisioning and revocation; the Android connector and its Network settings are already independent of fleet credentials.
4. Define public IPv4/IPv6, router, DNS and CGNAT requirements honestly.
5. Preserve HTTPS default and implement/test the optional plain-HTTP policy only if still required.
6. Resolve/document the 500 MB importer versus lower backup capacity.
7. Test actual 500 MB boundaries, low storage and device memory behavior.
8. Correct stale visitor-authentication UI copy.
9. Build APKs and complete emulator/physical-device tests; then test the owner's real domain from mobile data.
10. Keep the full fleet control plane optional for v0.1.0 and in v0.2.0 scope.

## 10. Evidence policy

The repository baseline records source presence and the exact CI runs. No item is release-complete until its relevant G0–G9 evidence exists. A passing CI suite is not a public-domain test or a physical-device acceptance.


## 11. Current Alpha snapshot delta

The source snapshot at `2087938a35dc88ed7588d97ffed8bd65fe8b40a9` contains 259 tracked files across 72 directories. Since the original `4185ea0` audit snapshot, a `relay-node/Cargo.toml` manifest has appeared. Inspection of the complete current `relay-node/` path list confirms that this is the only file in that directory. It declares dependencies and package metadata but does not provide an executable, tests, configuration, credentials, TLS, installer or service lifecycle. The previous audit conclusions about the absence of a working standalone Relay remain valid; only the wording “no package” is now outdated.

The Quality and Protocol workflows both completed successfully for the current Alpha head. These CI results validate the configured workflows; they do not prove that the Relay Node runs, that an APK is packaged, or that public-domain traffic reaches an Android host.


## 8. Remediation findings register

See [REMEDIATION_AUDIT.md](REMEDIATION_AUDIT.md) for the severity-ranked findings, evidence, fixes and verification states. It supersedes earlier point-in-time statements in this document where explicitly marked. Source changes have been made on the isolated remediation branch; no test result or release acceptance is claimed until the listed checks run.
