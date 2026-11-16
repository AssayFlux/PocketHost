# ASSAYFLUX POCKET HOST — PRIMARY RELEASE ROADMAP

**Product baseline:** 0.1.0 target · **Status:** pre-release · **Production approval:** not granted.  
**Authority:** `docs/NORTH_STAR.md` defines the product promise and ordered primary-version sequence. This roadmap defines executable gates and acceptance evidence.

## Product constraints

Native Kotlin/Jetpack Compose Android application; Android 8/API 26+; ARMv7/ARM64 target; no root or Termux. The phone is the website origin. Rust/Tokio/Hyper owns website ingress and routing. Static hosting works without Python, Node, Cloudflare, AssayFlux Relay or remote management. The owner's existing named Cloudflare Tunnel must be reused, not duplicated. The management API is never public. Python/Node remain trusted-code-only until per-project isolation passes.

## Gate 0 — Repository truth and architecture freeze (P0)

Audit manifests, permissions, source modules, native ABIs, dependency pins, workflows, docs and actual runtime behavior. Reconcile historical CI snapshots and conflicting status language. Commit Cargo, Gradle and npm lockfiles; pin Rust 1.88, checksum-verified Gradle 8.9 wrapper, Android SDK/NDK/CMake and native runtime inputs.

**Exit:** canonical evidence ledger is current; active documents distinguish source-present from verified; locked dependency resolution succeeds. **Implementation status: complete** at source baseline `b7775a2450c0633aaa44b58cb2cdb468a47ea0ca`.

## Gate 1 — Companion runtime migration (P0)

Complete companion lifecycle, Binder death/rebind, version negotiation, bounded transfer, crash-recoverable project replacement, process supervision and coordinated shutdown. Route real Python WSGI and Node HTTP requests through Rust. Preserve static-origin service through application-runtime failure. Keep runtime execution trusted-code-only until Gate 2.

**Exit during implementation:** source integration and regression-test definitions are complete. The Owner revised the sequence on 2026-11-16 and explicitly authorized intermediate Android APK/ABI builds and API 26/33/35 emulator instrumentation during Gates 2–4. Gate 8 remains responsible for final release packaging, physical-device acceptance and independent review.

## Gate 2 — Per-project isolation and adversarial security (P0)

**Current truth:** companion UID differs from host UID, but Python and Node projects share the companion UID and can potentially access sibling project data. Companion has INTERNET permission; loopback listener pinning is not outbound-egress denial. The isolated service/seccomp/Unix-socket work remains a proof spike, not a production sandbox. The probe now closes inherited socket descriptors before applying seccomp and its test source checks an inherited connected TCP socket plus socket/connect/bind denial. A host-side Binder adapter now obtains the real caller UID and mediates per-file read/write operations; the isolated probe exercises this callback and the test source checks host-UID spoof rejection. This remains a proof path, not production launcher integration. Chaquopy and Node still rely on companion app-private storage, which an isolated UID cannot simply reuse; runtime bootstrap/storage adaptation, lifecycle wiring and API 26/33/35 acceptance remain open.

**Implementation**
1. Prove isolated service UID, project-only file capability, brokered Unix-domain listener and native policy on API 26/33/35.
2. If descriptor access is denied by SELinux, choose a brokered descriptor-backed storage design; do not weaken SELinux.
3. Implement a bounded per-project cell supervisor with one project per isolated UID/process.
4. Move archive extraction and persistent project access inside that cell.
5. Move CPython interpreter and Node environment into their respective isolated cells; broker must never import customer code.
6. Install fail-closed OS policy before project code. Deny new socket creation, outbound connect, DNS/UDP/raw sockets, alternate socket APIs and prohibited syscalls. Verify actual kernel enforcement, not monkey patches.
7. Restrict file descriptors, process creation, memory, CPU time, storage, logs and restart rate; document Android limits.
8. Test two hostile projects concurrently, including native extensions, child processes, symlinks, traversal, procfs, loopback, IPv4/IPv6, DNS, metadata and management endpoints.
9. Complete independent adversarial review.

**Exit:** two real projects have distinct OS identities; cross-project/host/secret reads fail; outbound networking bypasses fail; visitor requests traverse only Rust's brokered ingress; static hosting survives cell failure; API matrix and independent review pass. If API 26 cannot support the selected policy, redesign rather than weakening the security boundary.

## Gate 3 — AssayFlux Relay and device identity (P0 new subsystem)

Build a first-party, independently deployable relay so users can publish without Cloudflare and without exposing their phone IP to ordinary visitors. A first bounded HTTP Relay path is now implemented: Owner DNS TXT hostname proof, verified host-to-device/site registry, authenticated outbound Android SSE stream, bounded request/response forwarding, exact local site-ID check, and immediate stream revocation on device token rotation/deletion. This is not yet a general streaming Relay: request/response bodies are capped at 8 MiB, request deadline is 45 seconds, four local requests run concurrently per device, and WebSocket upgrades, streaming downloads/SSE and HTTP/2 end-to-end multiplexing are not supported. Public TLS/DNS deployment and reconnect/load/soak validation remain unproven.

**Components:** public gateway, Android outbound tunnel agent, device enrollment broker, authenticated multiplexed streams, hostname registry, certificate lifecycle, visitor access gateway, rate/abuse controls, metrics and operations.

**Transport:** begin with a mature TLS-protected HTTP/2 stream design or reviewed reverse-tunnel protocol; WebSocket/TLS may be compatibility fallback. Evaluate QUIC only after reliable TCP transport. Do not invent cryptography. Use mutual device authentication, short-lived credentials, revocation, bounded buffers/streams and replay-resistant typed commands.

**Privacy:** standard relay mode terminates TLS at gateway and must disclose that gateway can inspect HTTP content. End-to-end TLS pass-through is a later advanced mode. Relay hides phone IP from ordinary visitors, not from relay operator or carrier.

**Exit:** device enrollment and revocation work; phone initiates outbound connection only; multiple streams route to correct Rust site; reconnect survives network changes; unauthorized hostname/device mappings fail; public management API is unreachable; Cloudflare and LAN modes remain functional.

## Gate 4 — Owner and visitor authentication (P0)

Separate Owner/Operator management identity from website visitor identity. Remote control-plane now has 12-hour revocable server-side admin sessions, hashed tokens, role enforcement and transactional audit. The Android host now supports site-scoped Public/Private/Restricted access, 256-bit one-time visitor credentials stored as SHA-256 hashes, Owner-only invite/revoke APIs, and browser Basic-auth challenge on protected sites; protected requests require HTTPS at a trusted loopback proxy and credentials are removed before proxying to customer runtimes. Still open: passkey-first Owner identity, fresh biometric/credential authorization for sensitive actions, and end-to-end Relay access tests. Rust now applies a bounded per-site/client visitor-authentication lockout.

**Exit:** cross-site token reuse, privilege escalation, revoked sessions and unauthorized relay access fail; visitor credentials never authorize management routes.

## Gate 5 — Rust governor and Capacity Lab (P0)

Implement fair global/per-site scheduling, bounded queues, request deadlines, slow-client defense, 429/503 retry hints, cancellation, static-origin reserve and graceful drain. Expand tests to cached/uncached files, response sizes, keep-alive, tunnel overhead and trusted Python/Node profiles. Measure RPS, throughput, p50/p95/p99, errors, queue depth, CPU, memory, temperature and battery.

**Exit:** repeatable safe operating envelopes with device/workload/duration evidence; no universal visitor count; static service survives runtime overload.

## Gate 6 — Backup, operations and lifecycle (P0)

Fault-test encrypted export/restore, wrong passphrase, corruption, interruption, low storage, activation failure and rollback. Exclude device-bound credentials. Add bounded redacted logs, durable diagnostics, alerting and support bundle. Test reboot, screen-off, Doze, battery saver, network transitions, low memory and service death.

**Exit:** last-known-good site remains recoverable; secret exclusion is tested; operational records are bounded and useful.

## Gate 7 — Product UI and onboarding (P1)

Finish Dashboard, Sites, Activity, Learn and Settings. Provide guided static publish, hostname/network selection, Cloudflare setup, Relay enrollment, visitor access policy, capacity profile and recovery. Preserve classic clean navy design, card hierarchy, monochrome icon style, light default and optional dark; no gradients/neon/glass.

**Exit:** first-time static publication needs no terminal; every displayed state reflects real backend state; accessibility, compact screen and visual acceptance pass.

## Gate 8 — CI, release candidate and independent review (P0)

Run all required jobs on one exact SHA. Build host and companion APKs for declared ABIs. Retain API 26/33/35 test reports, SBOM, dependency provenance, license audit, vulnerability scan, signing evidence and checksums. Verify Cloudflare and Relay externally. Complete physical ARMv7/ARM64 acceptance and independent security review.

**Exit:** signed host/companion APKs with matching signer; valid checksums; upgrade tests; external HTTPS; device evidence; independent review disposition; owner approval.

## Network product matrix

| Mode | Status | Responsibility |
|---|---|---|
| Local network | Existing local hosting path; device acceptance pending | LAN-only website access |
| Cloudflare Tunnel | Existing connector integration | Reuse owner's named tunnel and hostname |
| AssayFlux Relay | Partial bounded HTTP implementation in source; external deployment and acceptance open | First-party outbound tunnel and visitor access gateway |
| Direct port forwarding | Deferred advanced option | Owner-managed public exposure and firewall/TLS |

## Readiness reporting

Do not copy stale estimates forward. The canonical weighted score and evidence are in `docs/PROJECT_BASELINE.md`. A gate can increase only when its exit criteria have source, automated test, emulator/device or external evidence appropriate to the claim. Documentation work alone does not raise implementation readiness.

### 2026-11-16 implementation evidence addendum

- Gate 2: isolated-UID and seccomp probe hardening includes inherited connected-socket closure and socket/connect/bind denial assertions. It is not production runtime confinement; Python/Node storage bootstrap, actual Binder UID authorization, per-project service allocation, strict policy and API 26/33/35 adversarial acceptance remain open. Trusted-code-only policy remains mandatory.
- Gate 3: bounded HTTP Relay gateway, DNS TXT hostname proof, Android outbound SSE agent and request/response forwarding are implemented in source. The agent resolves the verified site ID to an enabled local site, routes with the local Host header while preserving the public hostname for proxied applications, and device token rotation/deletion closes the stream. Multiple Set-Cookie and WWW-Authenticate headers are preserved. Streaming/WebSocket support, public DNS/TLS deployment and load/soak evidence remain open. Keep Cloudflare Tunnel independent.
- Gate 4: remote admin sessions and transactional audit remain implemented. Local site-scoped Public/Private/Restricted visitor authorization and Owner invite/revoke controls are now implemented in Kotlin/Rust source; Android instrumentation source covers the management API lifecycle. Passkey-first Owner identity, visitor brute-force throttling and executed Android/native integration tests remain open.

## 2026-11-16 Gate 5–7 implementation increments

- Gate 5: per-site response admission now uses Tokio's FIFO-fair semaphore with a bounded 200 ms wait. Timed-out requests still receive HTTP 503 with Retry-After; the global active/queued socket caps remain in force. A Rust regression test covers timeout and recovery. This is short-burst FIFO fairness, not a weighted global scheduler; representative cached/uncached, payload, keep-alive, tunnel and trusted-runtime capacity profiles plus sustained target-device measurements remain open.
- Gate 6: Android instrumentation source now covers wrong-passphrase and ciphertext-tampering rejection before active-site mutation, in addition to the existing custom-root round-trip and duplicate-host rollback test. Operational log and telemetry rotation now fail closed rather than continuing to append to an oversized file if rotation cannot rename it; an instrumentation test covers normal rotation, redaction and configured file budgets. Low-storage, interrupted I/O, process-death recovery, rollback-failure injection and reboot/Doze/network/service-death acceptance remain open.
- Gate 7: the five destination screens and navigation source already exist; Learn onboarding copy now consistently directs users to Sites rather than the obsolete Files label. Settings remains embedded in the main activity. Terminal-free first-publication execution, Settings extraction, accessibility/TalkBack, compact-screen and visual screenshot acceptance remain open.
- All Android test additions above are source definitions only until the API 26/33/35 Android integration workflow reports successful execution and its artifacts are reviewed. Gates 5, 6 and 7 remain open.

## 2026-11-16 deployment recovery increment

- Gate 6: static deployment activation now has a startup recovery path. If Pocket Host starts and the active site is missing or lacks its entry page, it restores the newest retained release containing a valid index.html; invalid active data is preserved in the release directory for diagnosis. MainActivity invokes this before rendering the app and records recovery/failure events.
- Android instrumentation source covers recovery of a missing active site from the newest valid release. This does not yet provide crash journaling/recovery for every full-application backup phase or prove reboot/Doze/network/service-death behavior. API 26/33/35 execution remains unverified; Gate 6 remains open.

## 2026-11-16 five-destination UI test increment

- Gate 7: added Android UI instrumentation that selects Dashboard, Sites, Activity, Learn and Settings through accessible navigation labels, verifies selected-screen content, and captures a screenshot plus accessibility hierarchy XML for each destination. The Android integration workflow now collects those artifacts from each API-matrix emulator.
- This improves repeatable navigation and visual review coverage; it does not replace human TalkBack, font scaling, contrast, compact-screen or landscape acceptance. Gate 7 remains open pending executed evidence and completion of the full onboarding/Settings experience.

## 2026-11-16 latest Gate 5–7 follow-up

- Gate 5: fixed small-sample p95/p99 under-reporting by switching Capacity Lab to nearest-rank percentile calculation; added instrumentation assertions for two- and four-sample distributions.
- Gate 6: startup deployment recovery now rejects symlinked release trees and quarantines invalid active deployments; instrumentation covers both missing and invalid active-site recovery.
- Gate 7: extracted device preferences and section navigation into dedicated Compose Settings components; UI instrumentation now visits all four Settings sections and captures per-section screenshots/accessibility hierarchies.
- These are code and test-definition improvements. Android API 26/33/35 and Rust CI execution, artifact inspection, device lifecycle testing, capacity profiles and human accessibility review remain open. Gates 5–7 are not yet complete.

- Gate 5 additional regression: adb58da6fbdbc5b1bbb19f04ed5b6820879cada1 proves a saturated site's response semaphore does not block admission through a separate site's semaphore. This is unit-level isolation evidence only, not a live multi-tenant load/latency result.

- Gate 6 recovery hardening: invalid active websites without any safe fallback now emit a recovery error; instrumentation adds a symlinked-active-root case proving the link target is not activated.

## 2026-11-16 full backup restore recovery increment

- Gate 6: full app restore now persists a fsynced transaction journal before destructive renames and a separate commit marker only after site, routes and safe settings are committed. Startup recovery restores previous Python/Node projects, custom static roots, the previous static release, route registry and non-secret settings when a process dies mid-restore. Failed recovery preserves its remaining rollback material.
- Android instrumentation simulates a killed restore after partial static/Python/custom-root and preference mutation and verifies the prior state is restored. This source test remains unexecuted pending API 26/33/35 workflow evidence.

