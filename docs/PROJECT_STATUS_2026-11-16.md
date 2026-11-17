# AssayFlux Pocket Host — Engineering Status
**Snapshot date:** 2026-11-16  
**Product:** AssayFlux Pocket Host · **Developer:** Designed by AssayFlux  
**Status:** pre-release; not production-approved.

This report is an evidence-based snapshot, not a readiness percentage. Source implementation, automated tests, Android compilation, device acceptance, external deployment and production approval are separate states.

## 1. Repository and verification

- Repository: https://github.com/AssayFlux/PocketHost
- Branch: main
- Implementation source SHA reviewed: 06fa7dea1ed2481b30c680fd1a22ff083d48d203
- Quality workflow: https://github.com/AssayFlux/PocketHost/actions/runs/43851733175
- Result: completed success on that exact SHA; all six jobs passed: Rust, control-plane/PostgreSQL migrations, Python runtime, Node runtime, TypeScript console and Gradle wrapper.
- The quality workflow does not compile Android Kotlin, C++/JNI or package APKs. It does not run Android instrumentation.
- Owner sequencing remains in force: no APK build, Android compilation, emulator or device test during Gates 0–7. Those checks belong to Gate 8.

## 2. Implemented source foundations

- Kotlin/Jetpack Compose host app and separate runtime companion package.
- Rust/Tokio/Hyper website engine with static serving, hostname routing, reverse proxy, admission limits and site access enforcement.
- TypeScript/React/Vite remote console.
- Versioned signature-protected Binder IPC and bounded project archive transfer.
- CPython 3.11 / Chaquopy and Node mobile runtime source in the companion package.
- Owner-approved trusted-code gate for Python/Node.
- Archive extraction hardening, including traversal, duplicate, case-collision, file/directory collision and bounded-size checks.
- Isolated-UID/seccomp feasibility probe, inherited socket cleanup, Binder capability adapter using actual Binder.getCallingUid(), and Android instrumentation source for probe behavior.
- Remote control-plane device enrollment, user/device token rotation, 12-hour server-side admin sessions, role checks and transactional audit.
- Site-scoped Public/Private/Restricted visitor access, one-time 256-bit visitor credentials stored as SHA-256 hashes, Owner invite/revoke and Rust Basic-auth enforcement.
- Encrypted backup/restore foundations, telemetry and Capacity Lab foundations.
- Initial bounded HTTP AssayFlux Relay source implementation described below.

## 3. Gate-by-gate status

### Gate 0 — Repository truth and architecture freeze
**Complete.** Canonical docs and implementation boundaries are established.

### Gate 1 — Runtime migration
**Source implementation complete; Android verification deferred.** Python and Node are moved into the companion package. The source workflow is not proof of runtime startup or API/ABI acceptance.

### Gate 2 — Per-project security isolation
**Open. Do not treat the probe as production isolation.**

Implemented source:
- Lease registry and project-bound capability broker.
- Host Binder adapter obtains Binder.getCallingUid() at transaction receipt; UID is never accepted from a parcel.
- Read-only file descriptor and bounded write operations with per-lease grant limits and expiry.
- Isolated UID and Unix listener probe.
- Native probe closes inherited socket descriptors before seccomp, then tests denial of socket/connect/bind and brokered listener operation.

Still missing:
- Production cell supervisor and bounded per-project service allocation/death/revocation.
- Actual Python and Node execution in distinct isolated UIDs.
- Descriptor-rooted project/scratch storage and runtime-specific bootstrap/module/file adapters.
- Strict fail-closed syscall policy; current probe policy is denylist-based with an ALLOW fallback.
- Real visitor socket integration through production runtime cells.
- API 26/33/35 adversarial instrumentation and independent security review.

The production Python and Node workers still share the companion UID and app-private storage. Keep both owner-trusted-code-only. Do not weaken SELinux or claim hostile-code safety.

### Gate 3 — AssayFlux Relay
**Partial bounded HTTP implementation in source; not deployed or production-approved.**

Implemented source:
- Owner-only hostname registration and one-time DNS TXT ownership challenge; only the challenge hash is stored.
- DNS response checks for resolver source, transaction ID, question and answer owner. DNSSEC validation is not implemented.
- Verified hostname-to-device/site registry.
- Authenticated outbound Android SSE stream using the device bearer token.
- Bounded request/response forwarding: 8 MiB body cap, 45-second gateway deadline, bounded pending requests and four local request workers per device.
- Android agent resolves the exact site ID to an enabled local SiteRegistry entry and uses its local hostname for Rust routing. The verified public hostname is preserved as Host/X-Forwarded-Host for proxied applications.
- Raw loopback HTTP preserves Host semantics; Set-Cookie multiplicity, WWW-Authenticate, CORS and selected security headers are forwarded.
- Device token rotation/deletion revokes the in-memory Relay stream and pending requests.
- Visitor Basic-auth and per-site/effective-client-IP throttling are enforced by Rust on the phone after the request is relayed.

Still missing:
- WebSocket upgrades, streaming downloads/SSE and end-to-end HTTP/2 multiplexing.
- TLS/certificate lifecycle managed by AssayFlux and DNSSEC.
- Public Relay deployment, external DNS/TLS tests, network-transition tests and load/soak evidence.
- End-to-end validation with a real Android device and real visitor traffic.

The existing Cloudflare Tunnel remains independent. Do not describe this bounded HTTP path as arbitrary TCP or production-ready general Relay.

### Gate 4 — Owner/operator and visitor authentication
**Partially implemented; gate remains open.**

Implemented source:
- Remote admin credentials exchanged for 12-hour hashed server-side sessions.
- Session expiry, explicit revocation, user revocation and token-rotation invalidation.
- Owner/editor/viewer authorization and transactional audit.
- Owner-only team-user and device token rotation; old tokens are invalidated immediately.
- Local Public/Private/Restricted site access.
- One-time 256-bit visitor credentials stored as SHA-256 hashes; Owner invite/rotate/revoke.
- Rust constant-time Basic-auth comparison, HTTPS-only protected-site proxy enforcement and stripping of visitor Authorization before customer runtimes.
- Per-site/effective-client-IP visitor-authentication throttle: eight failures per 60-second window, then a 60-second cooldown; bounded bucket map.

Still missing:
- Passkey-first Owner identity and secure passkey recovery.
- Fresh reauthentication for sensitive actions.
- Full adversarial role and cross-site token testing on Android/native server.
- Executed Android instrumentation and physical-device acceptance.

## 4. Critical security and release limitations

- Companion UID separation is not per-project isolation.
- Python and Node still share companion identity and storage.
- Outbound egress is not OS-enforced per project in production.
- The isolated UID/seccomp/Binder path is probe-only and Android C++/Kotlin has not been compiled or executed.
- Relay is source-only; no public deployment or external visitor acceptance is claimed.
- No production-approved signed host/companion APK pair, physical-device acceptance, independent security review or release approval is recorded.
- No universal visitor count or uptime guarantee is made.

## 5. Required implementation order

1. Complete Gate 2 runtime-specific isolation design and production cell supervisor; migrate Python and Node only after descriptor-rooted runtime proofs.
2. Complete Gate 3 general Relay requirements, deployment, external acceptance and reliability evidence while preserving Cloudflare/LAN independence.
3. Complete Gate 4 passkey-first identity, fresh reauthentication and executed adversarial tests.
4. Continue Gates 5–7: governor/capacity, backup/operations and product UX.
5. At Gate 8 only: exact-SHA Android builds for declared ABIs, API 26/33/35 instrumentation, signed APK pair, physical device and external connectivity acceptance.

See docs/NORTH_STAR.md, docs/PROJECT_BASELINE.md, docs/ROADMAP.md, docs/MASTER_PLAN.md, docs/DEVELOPMENT_CONTEXT.md, docs/DECISIONS.md and the Gate 2/Gate 4 execution plans for detailed contracts and exit criteria.

**Designed by AssayFlux · AssayFlux Scientific Systems**


## Verification correction — 2026-11-16, 16:12 UTC

This section supersedes the earlier sequencing and verification statements above where they conflict.

- Current implementation SHA reviewed: `574b9ef3aa8ad226a0886cf4af87ee0dd6c3bdf0` on `main`.
- Quality workflow [43900937687](https://github.com/AssayFlux/PocketHost/actions/runs/43900937687) completed successfully on that exact SHA. All six jobs passed: Rust, control-plane/PostgreSQL, Python runtime, Node runtime, console and Gradle wrapper.
- Android integration workflow [43900937744](https://github.com/AssayFlux/PocketHost/actions/runs/43900937744) is still running on the same exact SHA. The cloudflared Android build and ABI verification passed; separate ABI debug APK builds and APK-content verification passed; the signature-matched runtime companion build passed. API 26 emulator instrumentation is in progress; API 33 and API 35 emulator jobs are queued. No API-matrix pass is claimed yet.
- Intermediate Android build and API-matrix validation is now intentionally being run during Gates 2–4, rather than deferred entirely to Gate 8. Gate 8 still requires final signed release artifacts, physical-device acceptance, external connectivity and independent review.
- Gate 2 remains open: the production Python/Node workers still share the companion UID and storage; the isolated UID/seccomp/Binder work is a feasibility probe, not production confinement.
- Gate 3 remains open: bounded HTTP Relay source exists but has no public deployment, managed TLS/DNS lifecycle, streaming/WebSocket completion or external visitor/soak acceptance.
- Gate 4 remains open: passkeys and complete fresh-reauthentication/security acceptance are not implemented/verified.
- Gates 5–7 remain open pending successful Android instrumentation, inspection of screenshots/hierarchy artifacts, representative device capacity measurements, lifecycle fault testing and human accessibility/onboarding review.
- Do not interpret APK compilation or a successful quality workflow as release approval, production security acceptance or closure of Gates 2–7.


## Verification update — 2026-11-17 00:24 UTC

- Current implementation SHA: `4f7d887ab26935dca0ca19a135b55e4b2f779d92` on `main`.
- Quality workflow [43983408019](https://github.com/AssayFlux/PocketHost/actions/runs/43983408019) completed successfully; all six jobs passed, including control-plane/PostgreSQL and deployment-profile checks.
- Android integration workflow [43983407943](https://github.com/AssayFlux/PocketHost/actions/runs/43983407943): cloudflared Android ABI build and ELF verification passed; separate ABI debug APK builds and APK-content verification passed; runtime companion build passed. Artifact `pockethost-debug-apks` was uploaded (GitHub artifact 14285116893).
- The APK artifact contains `app-arm64-v8a-debug.apk`, `app-armeabi-v7a-debug.apk`, `app-x86_64-debug.apk` and `pockethost-runtime-worker-debug.apk`. These are debug outputs, not signed release packages. The host APKs were extracted and SHA-256 verified for delivery. This is build/package evidence only.
- At this snapshot, API 26 emulator instrumentation is still running; API 33 and API 35 jobs are queued. No API-matrix pass is claimed. Screenshot/hierarchy and instrumentation report artifacts have not yet been reviewed.
- Gate 7 source increment: first-publish guidance is now present in the Sites empty state, with a test asserting the first-publish heading, top-level `index.html` guidance and start-hosting direction. This closes that narrow source/documentation task only; terminal-free publication execution and API-matrix execution remain unverified.
- Gate 2 remains open by owner instruction. Gates 3–7 remain open pending their implementation and evidence gaps; Gate 8 remains open for signed release, physical-device, external connectivity and independent review.
- Do not infer runtime correctness, release readiness, production isolation or gate closure from successful compilation/APK packaging.


## Android API 26 failure analysis — 2026-11-17 00:35 UTC

This evidence update supersedes the earlier statement that API 26 instrumentation was still running.

- Workflow [43983407943](https://github.com/AssayFlux/PocketHost/actions/runs/43983407943), implementation SHA `4f7d887ab26935dca0ca19a135b55e4b2f779d92`: API 26 emulator job completed **failure**, 50/55 tests passed and 5 failed. The test report artifact is [14286232244](https://github.com/AssayFlux/PocketHost/actions/runs/43983407943/artifacts/14286232244).
- `PocketHostNavigationAccessibilityTest.allFiveDestinationsExposeLabelsAndRenderTheirOwnScreen`: the Sites accessibility node was found but is not clickable; the test did not navigate and then timed out waiting for `WEBSITE`. The separate empty-Sites first-publish test passed.
- `PythonRuntimeSmokeTest` and `PythonMultiSiteSmokeTest`: both timed out waiting for the separately installed runtime companion Binder service. The captured per-test logcat did not establish the companion process's root cause; do not paper over this with a longer timeout.
- `RuntimeIsolationProbeTest`: the API 26 abstract Unix-socket client blocked while flushing before the service could accept it. The probe must coordinate the writer and Binder accept operation without blocking the test thread.
- `RuntimeWorkerSeccompTest.isolatedCellClosesInheritedSocketsDeniesNewSocketsAndAcceptsHostUnixListener`: setup failed with `EAFNOSUPPORT` while connecting its IPv4 loopback test socket. Use an API-26-supported deterministic socket-pair fixture or explicitly separate unavailable emulator networking from the isolation assertion; do not count this as a pass.
- At the time of this update, API 33 remained in progress and API 35 remained queued. No API 26/33/35 matrix pass is claimed.
- The above are unresolved defects. Gates 2 and 7 remain open; runtime migration and Android acceptance remain unverified. APK compilation and the six-job quality workflow are not substitutes for these tests.
