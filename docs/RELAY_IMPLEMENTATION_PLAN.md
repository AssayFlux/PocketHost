# Native Android Relay implementation plan

**Target:** Pocket Host v0.1.0 Alpha  
**Branch:** `Alpha`  
**Architecture:** Kotlin foreground service + Rust/Tokio native library through JNI  
**Status:** core native implementation is present; exact-SHA verification and physical/public acceptance remain open

This plan is the execution checklist for the approved native Android Relay architecture. It does not change the v0.1.0 scope or move Linux-native Relay installation into this release.

## Non-negotiable boundaries

- No Termux, terminal-managed scripts, Node/npm, Docker, PostgreSQL or separately installed Linux userspace is required by the Android Relay.
- The Android APK contains the Rust Relay library for every declared ABI.
- Visitor HTTP(S) traffic and authenticated outbound host sessions are separate protocol surfaces with separate authorization.
- The Relay must not expose management, pairing, fleet-control or runtime-worker APIs to visitors.
- Host credentials are independent from Cloudflare, fleet-admin, remote-control and local-management credentials.
- The native listener must never transmit host credentials over plaintext HTTP. Until TLS is configured and verified, the listener remains loopback-only.
- Relay failure must not stop the static website engine or Cloudflare Tunnel.
- Public acceptance requires an owner-controlled public IPv4 forwarding path or a supported IPv6 edge path. DNS alone cannot bypass CGNAT.

## Execution sequence

### R0 — Native gateway foundation

- [x] Add a separate Rust library crate in `relay-node/`.
- [x] Add JNI start/stop/status entry points.
- [x] Add bounded visitor request bodies, method allow-list, hostname/site routing and response-header allow-list.
- [x] Add authenticated outbound SSE host sessions and correlated response submission.
- [x] Bound pending requests globally and per host; reject stale or mismatched responses.
- [x] Restrict the current HTTP-only listener to loopback.
- [x] Add initial hostname, credential-digest and route-validation unit tests.
- [x] Add Android Gradle NDK packaging task for the Relay library.
- [x] Commit the generated Relay Cargo lockfile.
- [ ] Re-run Rust tests and all three Android ABI builds on the current Alpha checkpoint. Earlier PR runs passed these jobs, but subsequent path-policy/runtime changes reopened exact-SHA verification.

### R1 — Secure Android lifecycle and configuration

- [x] Implement an Android Keystore-backed AES-GCM encrypted Relay configuration store.
- [x] Add a dedicated, non-exported Relay foreground service with explicit start/stop/status and persistent notification.
- [x] Add initial owner controls to import the encrypted node config and start/stop/remove the service; this remains an engineering import flow, not final pairing.
- [x] Validate non-overlapping listener ports (HTTP 8082, HTTPS 8444 by default).
- [x] Add owner-enabled boot recovery for the native Relay service when encrypted Relay configuration exists.
- [x] Add network-change recovery; device verification and failure-isolation tests remain pending.
- [x] Add Android instrumentation for node identity, encrypted config, host credentials and credential issuance.
- [ ] Add dedicated Relay foreground-service/JNI lifecycle, notification/permission and failure-isolation instrumentation.

### R2 — TLS, hostname proof and route lifecycle

- [x] Select Rustls with the ring crypto provider and add a native HTTPS listener; Android ABI verification remains pending.
- [x] Implement ACME HTTP-01 certificate issuance, persisted certificate metadata and scheduled expiry/renewal checks.
- [ ] Verify issuance, renewal, expiry and recovery against a real public domain and the supported edge-forwarding topology.
- [x] Require a DNS TXT ownership challenge at startup before activating configured host routes.
- [x] Persist encrypted hostname/site/host configuration in transactional SQLite and record host revocations in a separate transactional table; migration/corruption recovery tests remain pending.
- [x] Keep unknown, unverified and revoked host routes fail-closed; startup requires DNS TXT ownership proof and certificate validity.
- [ ] Add continuous DNS ownership/revocation rechecks and prove expiry/revocation behavior with live DNS/TLS.
- [x] Redirect HTTP to HTTPS when TLS is configured.
- [x] Keep plaintext host-session authentication disabled on public interfaces; public binding requires TLS material and verified DNS TXT routes.

### R3 — Independent identity, pairing and credentials

- [x] Generate a stable P-256 node identity protected by Android Keystore; expose only its public identity and domain-separated challenge signatures.
- [x] Implement short-lived, single-use pairing invitations, host-key proof and explicit owner approval.
- [x] Issue 256-bit host ingress credentials in the owner UI; persist only SHA-256 verifiers in the encrypted node config.
- [x] Reuse the dedicated host-side RelayCredentialStore, which encrypts credentials with that host device's Android Keystore.
- [x] Implement host credential rotation and individual revocation; revocation removes the host's routes and persists a tombstone. Node-reset ceremony and live-disconnect integration tests remain open.
- [x] Commit config replacement and host-revocation tombstones in SQLite transactions; corruption/interrupted-write recovery tests remain open.

### R4 — End-to-end integration and hardening

- [x] Implement the visitor-to-authenticated-outbound-SSE host-session request/response path using the existing host-side RelayAgent wire contract.
- [x] Enforce initial request/response body, queue, concurrency and timeout limits.
- [x] Strip untrusted request headers and allow-list response headers.
- [x] Add native visitor-path rejection tests for Relay control namespaces, traversal, encoded separators/dots, double-encoding, malformed escapes and repeated slashes.
- [x] Add Rust visitor-path/control-surface rejection tests for Relay management namespaces, traversal and encoded path variants.
- [ ] Add end-to-end integration tests proving visitor requests cannot reach owner management, pairing, device-control, Workbench or runtime APIs through the full live gateway.
- [x] Add Rust restart/reconnect regression coverage for durable host identity, ephemeral session/pending state and fail-closed in-flight requests; pairing expiry/proof and host revocation tests are present.
- [ ] Exercise live disconnect/reconnect, stale/duplicate responses, host revocation and route removal on an Android device with real visitor traffic.
- [x] Implement Relay as an independently controlled foreground service; local hosting and Cloudflare Tunnel have separate service paths.
- [ ] Verify Relay failure/start/stop cannot interrupt local/LAN hosting or Cloudflare Tunnel on physical devices.

### R5 — Release verification

- [ ] Re-run Rust unit/security tests and protocol contract tests on the current Alpha checkpoint (prior PR checks passed; latest exact-SHA runs are pending).
- [ ] Rebuild and inspect Relay native libraries for arm64-v8a, armeabi-v7a and x86_64 on the current Alpha checkpoint (prior PR all-ABI builds passed).
- [ ] Inspect APK contents, package metadata, signatures and SHA-256 hashes.
- [ ] Run Android emulator instrumentation on API 26, 33 and 35.
- [ ] Install and upgrade on named physical devices across the declared matrix.
- [ ] Verify public DNS TXT, valid HTTPS, HTTP redirect and visitor content from independent networks.
- [ ] Verify a separate host phone behind NAT can establish its outbound TLS session.
- [ ] Verify unknown/revoked hosts and management paths fail closed.
- [ ] Test screen-off, Doze, process death, reboot, network transitions, storage pressure and recovery.
- [ ] Record source SHA, APK SHA-256, device/OS/ABI, network topology, DNS/TLS observations, logs, soak measurements and G0–G9 evidence.

## Required default topology

| Purpose | Android listener | Public edge |
|---|---:|---:|
| Existing static website engine | 8080 | Local/LAN only unless separately exposed |
| Relay HTTP | 8082 | TCP 80 → 8082 (redirect only after TLS is ready) |
| Relay HTTPS | 8444 | TCP 443 → 8444 |
| Python runtime default | 8081 | Not a public Relay listener |

Android non-root applications cannot bind privileged ports 80/443 directly. The owner must configure router/edge forwarding or a supported reverse proxy. A phone behind CGNAT with no public IPv6 and no inbound edge path cannot accept unsolicited public visitor traffic; Cloudflare Tunnel remains a separate outbound fallback.

## Current engineering checkpoint (2026-11-20)

The native Android Relay implementation is present in Alpha: Kotlin foreground service and JNI bridge; Rust/Tokio bounded visitor gateway; authenticated outbound SSE host sessions; Android Keystore node identity; AES-GCM encrypted transactional node configuration; durable pairing-invite hashes, credential verifiers and revocation records; single-use owner-approved host pairing; owner credential rotation/revocation; DNS TXT activation proof; Rustls HTTPS, HTTP redirect and ACME HTTP-01 issuance/renewal scheduling. The Rust engine rejects visitor control-surface paths and has restart tests proving durable host identity with fail-closed pending requests. Android tests cover node identity/configuration and host credential flows. Sensitive credential paths are filtered across Android import/backup, Rust static serving and runtime project transfer.

Still open: continuous DNS ownership/revocation checks; real-domain ACME issuance/renewal; complete visitor/owner route-isolation and live revocation/reconnect tests; dedicated foreground-service/JNI/notification permission tests; exact-current-SHA Rust/ABI/Android CI; full API 26/33/35 emulator matrix; APK inspection/signature/hash/provenance; and all physical-device, external-network, battery/Doze, process-death, reboot, low-storage and soak acceptance. These implementation milestones do not close any G0–G9 gate.

**Release rule:** no G0–G9 gate is considered PASS based on this plan, a source file, compilation or emulator-only evidence. v0.1.0 remains open until every required gate is evidenced and the owner accepts the physical-device/public-network results.
