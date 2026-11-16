# ASSAYFLUX POCKET HOST — CANONICAL PROJECT BASELINE

**Baseline review:** 2026-11-16  
**Source reviewed:** `e9d7c9a95304ac16e55f988f509ef0f44383034a` (latest six-job quality run; subsequent commits update documentation only)  
**Release:** 0.1.0 target · **Status:** pre-release · **Production approval:** no.

This is the current evidence baseline. Source implementation is unchanged since the wrapper/dependency freeze; this commit records the six-job exact-SHA quality result.

## Product contract

Native Android phone-hosted website origin; API 26+; ARMv7/ARM64; no Termux/root; Rust/Tokio/Hyper request engine; static hosting independent of optional services; TypeScript/React console; existing Cloudflare Tunnel reused; AssayFlux Relay planned; management API never public; Python/Node trusted code only until per-project OS isolation is proven.

## Current implementation truth

| Subsystem | Source-level position | Verification / limitation |
|---|---|---|
| Android host | Kotlin/Compose app, five destinations, foreground hosting service | Intermediate APK/ABI and API 26/33/35 emulator validation authorized during Gates 2–4; final physical-device/release acceptance remains Gate 8 |
| Rust engine | Tokio/Hyper, static files, hostname routing, reverse proxy, bounded admission | Non-Android Rust tests pass; fairness and sustained-load evidence remain |
| Python | CPython 3.11/Chaquopy moved into companion package | Python-through-Rust instrumentation test is written; not run yet |
| Node | Node mobile 24.22.1 native runtime in companion, dedicated process | Node-through-Rust and static-origin continuity tests are written; not run yet |
| Companion | Separate package/UID, signature Binder, caller checks, protocol negotiation, Binder death handling, rebind and orderly shutdown | Instrumentation source exists; API 26/33/35 execution is now part of Gates 2–4 and remains unverified |
| Project transfer | Bounded archive, staged extraction and recoverable previous-project rename | Source hardening present; Android transfer/rollback tests await Gate 8 |
| Isolation | Isolated-service, Unix-domain and native seccomp proof source exists | Not a production per-project sandbox; Python/Node remain trusted-only |
| Management | Authenticated loopback API and TypeScript/React console | External deployment and full security acceptance remain |
| Cloudflare | Android cloudflared build integration; existing named tunnel is reused | External hostname/TLS verification remains |
| AssayFlux Relay | Bounded HTTP gateway, Android outbound SSE agent, verified hostname/device/site routing and request/response forwarding in source | Public TLS/DNS deployment, certificate lifecycle, streaming/WebSocket, load/soak and external visitor acceptance remain open |
| Capacity | Device telemetry, first Capacity Lab and admission derating | Fair governor, workload coverage and safe-envelope evidence remain |
| Backup | Encrypted .afbackup and staged restore foundations | Full fault matrix remains |
| Control plane | Axum/PostgreSQL source, device/user token rotation, 12-hour revocable admin sessions and audit | Deployment and device E2E not proven |
| Release | Manual signed-release workflow source | No verified signed production APK pair |

## Reproducible build inputs

- Rust toolchain/MSRV: 1.88.0; `.cargo/config.toml` selects Rust-version-compatible dependency resolution.
- Cargo locks: `rust-engine/Cargo.lock`, `control-plane/Cargo.lock`.
- Gradle: wrapper 8.9, distribution SHA-256 pinned in `gradle/wrapper/gradle-wrapper.properties`; app and companion Gradle dependency lockfiles committed.
- Android: SDK platform 35, Build Tools 35.0.0, NDK 27.2.12479018, CMake 3.22.1.
- Native Rust Android tool: cargo-ndk 4.1.2 installed with `--locked`.
- Console: exact direct package versions and `web-console/package-lock.json`; CI uses `npm ci`.
- Python: 3.11; Node console CI: 22; Node runtime smoke CI: 24; cloudflared build Go toolchain: 1.27.4.
- Dependency lock workflow resolves graphs and commits lockfiles; it does not build APKs.

## CI and Android build policy

- Quality workflow: Rust, control plane/database migrations, Python, Node, console and Gradle wrapper validation. It does not compile Android or package APKs.
- Android integration now runs on relevant `main` pushes and can be manually dispatched; signed-release remains a separate release workflow.
- Revised Owner instruction (2026-11-16): run intermediate Android APK/ABI builds and API 26/33/35 emulator instrumentation during Gates 2–4 so security changes are validated as implemented. Gate 8 retains final release packaging, physical-device acceptance and independent review.
- A legacy Android integration run `43824043298` on SHA `12eaf3ba700b6e0c37ecc14f72e633c5800fd431` started before the manual-only change and is now cancelled. Its cloudflared job succeeded, APK job failed, API 26/33 emulator jobs failed, and API 35 was cancelled. It produced no accepted APK or API-matrix result. The replacement push-triggered run `43828040040` skipped all Android jobs.

## Verification evidence

- Quality run `43827669251`: all six jobs passed on `9f249f0faf3f103bcee9aaa763adf3c275211951` (Rust 1.88 locked tests, control-plane migrations and Compose profiles, Python, Node, `npm ci`, Gradle wrapper validation).
- Dependency-lock workflow `43826809601`: completed successfully; generated Cargo/npm/Gradle locks and the checksum-verified Gradle wrapper.
- No successful Android APK artifact, accepted API matrix or physical-device result is claimed on this baseline.

## Gate status

| Gate | Status |
|---|---|
| 0 — Repository truth and architecture freeze | Complete |
| 1 — Runtime migration | Source implementation complete; intermediate Android validation pulled into Gates 2–4; final release acceptance remains Gate 8 |
| 2 — Per-project isolation and adversarial security | Open; shared companion UID is not a sandbox |
| 3 — AssayFlux Relay | Partial source implementation; external deployment and end-to-end validation open |
| 4 — Owner/visitor authentication | Open |
| 5 — Rust governor and Capacity Lab | Open |
| 6 — Backup, operations and lifecycle | Open |
| 7 — Product UX and onboarding | Open |
| 8 — Android builds, API matrix, release and independent review | Deferred until all earlier implementation work is complete |

No current percentage is published. Source presence, compilation, automated testing, emulator verification, physical-device verification and production approval are separate states.

## Non-negotiable security limitations

- Companion UID separation is host-vs-companion only; it is not per-project isolation.
- Python/Node share companion UID and storage.
- Outbound egress is not OS-enforced per project.
- Seccomp/isolated UID/Unix-domain probes are not production runtime confinement.
- Keep runtimes limited to owner-controlled trusted code.
- Relay must not expose management API and must disclose that gateway-terminated TLS allows relay inspection.
- A relay hides the phone's IP from ordinary visitors, not from relay infrastructure or carrier.

Update this file with exact evidence whenever the baseline changes.

## 2026-11-16 incremental source and CI evidence

- Latest previously completed quality run: `43836090906` on `5de2ff28b880119de379a52885b17abecec23e06`; all six jobs completed successfully (Rust, control-plane, Python, Node, console and Gradle wrapper).
- Subsequent control-plane source increment: `dc18daa9855debdeb8fe7b5ed291b9ee8365bdcb` adds exclusive create-new deployment artifact writes, file and parent-directory fsync, and cleanup of partial files or database-transaction failures, plus a regression test for cleanup, committed-file retention and overwrite rejection. The control-plane README documents the transactional audit and artifact lifecycle.
- CI for that source increment was queued at review time; do not treat the earlier successful run as verification of the new code.
- Gate 2 remains open: no production per-project isolated runtime migration or accepted Android API matrix.
- Gate 4 remains open: remote control-plane device and team-user token rotation, role checks and atomic audit writes are implemented; passkeys and site-scoped visitor authorization are not complete.
- Latest incremental commits add an Owner-only team-user token rotation endpoint, PostgreSQL-backed revocation/audit regression test, OpenAPI contract and console action. Source: `11b30b3`, `0aa1f1f`, `59bf1e7`, `7ea7c22`, `c4fcb48`. CI for these changes is pending.


## 2026-11-16 Stage 2 incremental source work

- Native feasibility probe now enumerates `/proc/self/fd` and closes inherited socket descriptors except the broker-created Unix visitor listener before seccomp installation. Descriptor cleanup fails closed; the probe detaches ownership of the deliberately supplied descriptor before native closure to avoid closing a subsequently reused FD.
- Instrumentation source now supplies a connected loopback TCP socket and checks that the isolated service closes it, denies new socket creation, and still accepts the brokered Unix visitor stream with the expected peer UID.
- Source commits: `e1a1e1d`, `8d9b87a`, `be0b1fb`, `1b2e7b0`, `bd6af74`, `e8b4616`, `3986990`; documentation commits `7815887`, `fd0eef3`.
- This is feasibility-probe hardening only. It does not migrate Python/Node into isolated services, provide project directory capabilities, establish a strict syscall allowlist, or pass the API 26/33/35 adversarial matrix. Gate 2 remains open. No Android build or instrumentation execution was performed, per the Gate 8 build policy.
- The latest six-job quality workflow before these changes passed on `1497f8b` (run `43837524062`). The native Android C++ and instrumentation changes have not been compiled or executed by that workflow; a new workflow run must be checked for the non-Android jobs only.


## 2026-11-16 Gate 2–4 implementation evidence

- Latest six-job quality run: `43841652274`, completed success on `e9d7c9a95304ac16e55f988f509ef0f44383034a` (Rust, control-plane/PostgreSQL migrations, Python, Node, console and Gradle wrapper).
- Gate 2 source: inherited socket descriptors are closed before seccomp installation in the isolated-UID probe; instrumentation source checks an inherited connected TCP socket is closed and `socket`, `connect`, `bind` are denied while the brokered Unix listener remains usable. A host-side Binder adapter obtains `Binder.getCallingUid()` at transaction receipt and mediates read-only PFD opens and bounded writes; the isolated probe calls it, and instrumentation source checks host-UID spoof rejection and project-file read/write. This is not production Python/Node confinement. Android C++/Kotlin instrumentation was not compiled or run; no API 26/33/35 evidence is claimed.
- Gate 2 architecture research: `android:isolatedProcess` services have no permissions; `bindIsolatedService` multi-instance binding begins at API 29, requiring fixed service slots on API 26–28. Chaquopy and the current Node worker use app-private runtime/project/cache paths; they need descriptor-rooted storage and runtime-specific bootstrap adaptation before isolated execution can be enabled.
- Gate 3 now has a bounded HTTP Relay implementation in source: Owner DNS TXT hostname proof, verified host-to-device/site routing, authenticated outbound Android SSE stream, request/response forwarding, local site-ID validation and immediate device-session revocation. Limits: 8 MiB request/response bodies, 45-second deadline, four concurrent local requests per device; no WebSocket upgrades or streaming response bodies. The phone resolves the site ID to an enabled local hostname, preserves the public host for runtime proxying, and forwards multiple Set-Cookie and WWW-Authenticate headers. Visitor auth is throttled per site/client IP. No WebSocket/streaming response support, DNSSEC, TLS/certificate lifecycle, external deployment or load/soak evidence is claimed.
- Gate 4 source: 12-hour hashed and revocable remote admin sessions, role enforcement, token rotation and transactional audit are covered by PostgreSQL CI. Local site-scoped Public/Private/Restricted access, one-time 256-bit visitor credentials stored as SHA-256 hashes, Owner invite/revoke APIs, and browser Basic-auth enforcement in Rust are implemented in source. Protected sites require HTTPS at the trusted loopback proxy; visitor Authorization is stripped before proxying to customer runtimes. Five-minute fresh credential checks for remote writes and sensitive local Owner mutations are implemented, with console reauthentication and session replacement. Passkey-first identity, biometric-bound step-up, complete route-by-route tests and executed Android integration tests remain open.
- The new Android Binder/seccomp instrumentation source has not been compiled or executed; the six-job CI workflow does not build the Android app/native worker. Android build/device validation and external Relay deployment remain deferred/unverified. No production-ready claim is made.


## 2026-11-16 latest Gate 2–4 source changes

- Gate 2 remains open. The Binder capability adapter and isolated probe remain feasibility work; the production Python/Node runtime still shares the companion UID and app-private project/runtime storage. No production cell supervisor, runtime migration or Android API 26/33/35 test result is claimed.
- Gate 3 source now routes an authenticated visitor request to the selected enabled local site ID, uses its configured local hostname for Rust routing, and preserves the verified public hostname for runtime X-Forwarded-Host. The Android agent uses bounded raw loopback HTTP to preserve Host; response cookies and browser Basic-auth challenges are retained. DNS TXT parsing checks response source, transaction ID, question and answer owner. DNSSEC is not validated. Streaming/WebSocket, TLS/certificate lifecycle, deployment, external visitor tests and soak evidence remain open.
- Gate 4 now has per-site/effective-client-IP visitor-authentication throttling in Rust (8 failed attempts per 60-second window, then a 60-second cooldown, with a bounded bucket map). Remote 12-hour sessions, role enforcement, user/device token rotation, audit and site-scoped visitor credential revocation remain implemented in source. Five-minute credential reauthentication is implemented in source; passkey-first identity and biometric-bound step-up remain open.
- Latest six-job quality workflow `43853226788` completed success on exact SHA `5a2eb78c624e122dbf38a91fcf49b1750c8599b1`: Rust, control-plane/PostgreSQL migrations and Compose profiles, Python runtime, Node runtime, console and Gradle wrapper all passed. This workflow does not compile Android Kotlin/C++ or execute Android instrumentation. No Android build/device test was run, per the owner-directed Gate 8 sequencing.


## Latest verified quality evidence — 2026-11-16

- Repository HEAD at verification: 5a2eb78c624e122dbf38a91fcf49b1750c8599b1.
- Workflow: https://github.com/AssayFlux/PocketHost/actions/runs/43853226788 — completed success, all six jobs successful.
- Control-plane tests ran against PostgreSQL 16, including migrations, remote session expiry/revocation, team-user token rotation, device token rotation, Relay hostname state, bounded pending-request quotas, DNS TXT parser validation, dedicated control-host matching and artifact transaction tests. Caddy strict SNI validation and both Compose ingress profiles passed.
- Rust engine tests passed, including per-site/client visitor-auth throttling tests. Python, Node, TypeScript console and Gradle-wrapper jobs passed.
- The six-job quality workflow does not compile Android app Kotlin, companion Kotlin, C++/JNI, nor execute Android instrumentation. A separate push-triggered Android integration workflow is configured for relevant `main` changes, but no successful run/result is verified in this baseline.


## Relay ingress separation — 2026-11-16

- The Axum control plane now binds two listeners in one process with shared state: the dedicated control/device API listener (POCKETHOST_CONTROL_BIND, port 8080) and the visitor-only Relay gateway listener (POCKETHOST_RELAY_BIND, port 8081).
- The visitor listener has only the verified-host fallback; it does not register /v1/admin, /v1/device, health or management routes. The control listener carries admin/device routes and is guarded by POCKETHOST_CONTROL_API_HOST.
- Caddy routes the dedicated control hostname to 8080 and other verified Relay visitor hostnames to 8081. strict_sni_host on is enabled to reject SNI/Host domain-fronting mismatches. The Cloudflare Tunnel profile must route the management hostname to 8080 and visitor hostnames to 8081.
- This is source and CI-validated ingress separation; no live Caddy/Cloudflare deployment or external visitor acceptance is claimed.


## Owner-approved intermediate Android validation — 2026-11-16

- The Owner explicitly changed the sequencing: Android APK/ABI builds and API 26/33/35 emulator instrumentation are required during Gates 2–4, not deferred wholesale to Gate 8.
- `.github/workflows/android-integration.yml` now runs on relevant pushes to `main`; it builds cloudflared Android ABIs, the host and companion debug APKs, and runs the API 26/33/35 emulator instrumentation matrix. The workflow definition is source evidence only; a successful run and its artifacts have not yet been verified here.
- Gate 2 source hardening after the earlier snapshot: root and intermediate directory opens now use `O_NONBLOCK`; Android instrumentation source rejects FIFO roots, FIFO path components, symlinked files and symlinked directories. The duplicate `O_NONBLOCK` flag in root identity capture was removed. The production Python/Node launcher still does not use per-project isolated UIDs or the Binder capability broker, so Gate 2 remains open.
- Gate 4 instrumentation source now qualifies the Owner step-up policy helper through `ManagementApiServer`, fixing unresolved test references found during review. This is a source correction, not a test result.
- Source changes are on `main` through `2357ed5301617ae0b63b35915779d4f4b44b44ad`. No Android compile, emulator result, external Relay deployment, physical-device test or independent security review is claimed. Gates 2, 3 and 4 remain open.

## 2026-11-16 Gate 5–7 source increments

- Gate 5 Rust change: 8fc83afdb30a6a6b59ccc972e1c924873fc3aaac adds a bounded FIFO per-site response-permit wait and a timeout/recovery test. This is not weighted cross-site scheduling or workload capacity proof.
- Gate 6 source: ec933ba91d4718c91b0265189a83d2bffb87742b adds Android instrumentation for wrong passphrases and modified AES-GCM ciphertext failing before active-site mutation. 1817a33957804d18d797dbe5b90a28939ed07150 and 478bf1bc26e797ffceaabd138b0cd67d44510352 prevent operational logs and telemetry from growing without bound when rotation fails. 1b964419e9419c7ae113170d8472dfac175671d8 adds instrumentation coverage for log redaction and normal bounded rotation.
- Gate 7 source: 714fede2e30862455cc6c8c945b98a42102d8747 aligns Learn onboarding labels with the actual Sites navigation destination. Dedicated Dashboard, Sites, Activity and Learn Compose files already exist; Settings is still embedded in the main activity.
- Documentation updates: 1a7f13e6e964311e92bfe22634f8c07623e386ad, 13829144d6681afe1e82089e1ef82bcd2e5fc7e5, 389adde1713d52aee545bbd5e38ba8a3816a0710.
- These commits add source and test definitions, not executed acceptance evidence. The Rust quality workflow and Android API 26/33/35 workflow must be checked on the resulting exact HEAD before any verification claim. Gates 5, 6 and 7 remain open.

## 2026-11-16 interrupted deployment recovery source

- 7bc5556d2bb5026244777419b27d57c7817347ec adds SiteImporter recovery of the newest retained valid release when the active site is missing or invalid.
- 0a2909910f4c5a4d68f194337ccc5c40e075d2ad invokes that recovery before MainActivity renders and records a redacted operational event.
- 4fc9582ebc78cf4d9860e76b3455a554b713b539 adds Android instrumentation source for recovery from a missing active site.
- This covers interrupted static deployment activation only. Full application backup restore journaling, process-death recovery across all restore phases and Android lifecycle/device acceptance remain open.

## 2026-11-16 UI navigation instrumentation source

- 7c55388f54313ae81cf3b5d644bf21f102615745 adds a UIAutomator instrumentation test for all five destinations and verifies destination-specific content.
- b36858a4581a69eebf428b4ec6d86d2c74f722d2 retains per-destination screenshots and accessibility hierarchy XML; 77f3cb655365cd29c01a88e5f664ce40bfd12b34 updates Android CI to collect them.
- These artifacts have not yet been produced/inspected by a successful API 26/33/35 workflow. Human accessibility and responsive visual acceptance remain open.

## 2026-11-16 latest Gate 5–7 source follow-up

- Rust governor: 62c15054cf28474250bbc64110a0748df7146d24 clarifies the distinction between the bounded socket queue and protocol-bounded HTTP/2 request concurrency. FIFO per-site permit waiting remains bounded by 200 ms.
- Gate 7: a27556e822f044fc9bcaaa046d721c19393d0865 and 2bc01c398741b9ccf9109f44b02195e85111fd8d align the Learn screen callback name with the actual Sites destination.
- UI instrumentation and artifact collection remain in source at 7c55388f54313ae81cf3b5d644bf21f102615745, b36858a4581a69eebf428b4ec6d86d2c74f722d2 and 77f3cb655365cd29c01a88e5f664ce40bfd12b34.
- GitHub combined commit-status lookup returned no status entries for the latest source commit at review time. This is not evidence of workflow success or failure; inspect Actions runs/artifacts before claiming Android or Rust verification.
- Gates 5, 6 and 7 remain open pending weighted/global fairness and representative capacity evidence, full backup/lifecycle fault acceptance, and executed UI/accessibility/onboarding review.

## 2026-11-16 Capacity Lab tail-latency increment

- f9fd4f4f6773e1fc79f693e65b9e07abb8a60467 adds p99 latency to each Capacity Lab concurrency result.
- 19b963158b68ea685339b2050195a985220354e5 and c788553eed7307874c7ca0e6ae2f6d21c88192e9 display p95/p99 in the dashboard and identify legacy benchmark records without p99 as unavailable.
- No benchmark or Android build execution is claimed; representative workload profiles and target-device measurements remain open.

