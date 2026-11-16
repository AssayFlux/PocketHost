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

**Exit during implementation:** source integration and regression-test definitions are complete. By owner instruction, APK builds, ABI packaging tests and API 26/33/35 emulator/device execution are deferred to Gate 8; do not run intermediate Android builds.

## Gate 2 — Per-project isolation and adversarial security (P0)

**Current truth:** companion UID differs from host UID, but Python and Node projects share the companion UID and can potentially access sibling project data. Companion has INTERNET permission; loopback listener pinning is not outbound-egress denial. The isolated service/seccomp/Unix-socket work is a proof spike, not a production sandbox.

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

Build a first-party, independently deployable relay so users can publish without Cloudflare and without exposing their phone IP to ordinary visitors.

**Components:** public gateway, Android outbound tunnel agent, device enrollment broker, authenticated multiplexed streams, hostname registry, certificate lifecycle, visitor access gateway, rate/abuse controls, metrics and operations.

**Transport:** begin with a mature TLS-protected HTTP/2 stream design or reviewed reverse-tunnel protocol; WebSocket/TLS may be compatibility fallback. Evaluate QUIC only after reliable TCP transport. Do not invent cryptography. Use mutual device authentication, short-lived credentials, revocation, bounded buffers/streams and replay-resistant typed commands.

**Privacy:** standard relay mode terminates TLS at gateway and must disclose that gateway can inspect HTTP content. End-to-end TLS pass-through is a later advanced mode. Relay hides phone IP from ordinary visitors, not from relay operator or carrier.

**Exit:** device enrollment and revocation work; phone initiates outbound connection only; multiple streams route to correct Rust site; reconnect survives network changes; unauthorized hostname/device mappings fail; public management API is unreachable; Cloudflare and LAN modes remain functional.

## Gate 4 — Owner and visitor authentication (P0)

Separate Owner/Operator management identity from website visitor identity. Implement passkey-first owner login, recovery, scoped Operator roles, device approval, session expiry/revocation and fresh authorization for sensitive actions. Visitor modes: Public, Private and Restricted, with site-scoped short-lived tokens, brute-force controls and audit.

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
| AssayFlux Relay | Planned Gate 3 | First-party outbound tunnel and visitor access gateway |
| Direct port forwarding | Deferred advanced option | Owner-managed public exposure and firewall/TLS |

## Readiness reporting

Do not copy stale estimates forward. The canonical weighted score and evidence are in `docs/PROJECT_BASELINE.md`. A gate can increase only when its exit criteria have source, automated test, emulator/device or external evidence appropriate to the claim. Documentation work alone does not raise implementation readiness.

### 2026-11-16 implementation evidence addendum

- Gate 2: source primitives and probes are present, but production Python/Node per-project isolated execution and adversarial API 26/33/35 acceptance remain open. Trusted-code-only policy remains mandatory.
- Gate 4: remote control-plane device and team-user token rotation (including an Owner-only console action), role enforcement and mutation-plus-audit transactions are implemented in source. Passkey-first identity and site-scoped visitor Public/Private/Restricted authorization remain unimplemented; the gate is therefore open.
