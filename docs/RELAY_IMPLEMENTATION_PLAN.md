# Native Android Relay implementation plan

**Target:** Pocket Host v0.1.0 Alpha  
**Branch:** `Alpha`  
**Architecture:** Kotlin foreground service + Rust/Tokio native library through JNI  
**Status:** implementation in progress; not release accepted

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
- [ ] Pass Rust tests and all three Android ABI builds.

### R1 — Secure Android lifecycle and configuration

- [x] Implement an Android Keystore-backed AES-GCM encrypted Relay configuration store.
- [x] Add a dedicated, non-exported Relay foreground service with explicit start/stop/status and persistent notification.
- [x] Add initial owner controls to import the encrypted node config and start/stop/remove the service; this remains an engineering import flow, not final pairing.
- [x] Validate non-overlapping listener ports (HTTP 8082, HTTPS 8444 by default).
- [x] Add owner-enabled boot recovery for the native Relay service when encrypted Relay configuration exists.
- [x] Add network-change recovery; device verification and failure-isolation tests remain pending.
- [ ] Add service/JNI instrumentation tests and notification/permission tests.

### R2 — TLS, hostname proof and route lifecycle

- [x] Select Rustls with the ring crypto provider and add a native HTTPS listener; Android ABI verification remains pending.
- [x] Implement automatic ACME HTTP-01 certificate issuance/renewal, persist account credentials and certificate/key in encrypted configuration, and check expiry every 12 hours with renewal inside the 30-day window. Real-domain issuance/renewal acceptance remains pending.
- [x] Require a DNS TXT ownership challenge at startup before activating configured host routes.
- [x] Persist encrypted hostname/site/host configuration in transactional SQLite and record host revocations in a separate transactional table; migration/corruption recovery tests remain pending.
- [x] Keep unknown, unverified and revoked hostnames fail-closed at route activation; recheck DNS TXT ownership at startup. Continuous DNS TXT lifecycle revalidation remains open.
- [x] Redirect HTTP to HTTPS when TLS is configured.
- [x] Keep plaintext host-session authentication disabled on public interfaces; public binding requires TLS material and verified DNS TXT routes.

### R3 — Independent identity, pairing and credentials

- [x] Generate a stable P-256 node identity protected by Android Keystore; expose only its public identity and domain-separated challenge signatures.
- [x] Implement two-minute single-use owner-issued pairing invites, bound to configured host routes, with host P-256 key-possession proof over verified HTTPS.
- [x] Issue 256-bit host ingress credentials in the owner UI; persist only SHA-256 verifiers in the encrypted node config.
- [x] Reuse the dedicated host-side RelayCredentialStore, which encrypts credentials with that host device's Android Keystore.
- [x] Implement host credential rotation and individual revocation; revocation removes the host's routes and persists a tombstone. Node-reset ceremony and live-disconnect integration tests remain open.
- [x] Commit config replacement and host-revocation tombstones in SQLite transactions; corruption/interrupted-write recovery tests remain open.

### R4 — End-to-end integration and hardening

- [x] Implement the visitor-to-authenticated-outbound-SSE host-session request/response path using the existing host-side RelayAgent wire contract.
- [x] Enforce initial request/response body, queue, concurrency and timeout limits.
- [x] Strip untrusted request headers and allow-list response headers.
- [x] Add native visitor-path rejection tests for Relay control namespaces, traversal, encoded separators/dots, double-encoding, malformed escapes and repeated slashes.
- [ ] Verify visitor routes cannot reach owner management, pairing, device-control, Workbench or runtime APIs with integrated automated negative tests.
- [ ] Test disconnect/reconnect, stale request IDs, duplicate responses, host revocation and route removal.
- [ ] Verify Relay can start/stop independently and cannot break local/LAN website hosting or Cloudflare Tunnel.

### R5 — Release verification

- [ ] Run Rust unit/security tests and protocol contract tests.
- [ ] Build and inspect Relay native libraries for arm64-v8a, armeabi-v7a and x86_64.
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

## Current engineering checkpoint

The Rust gateway/JNI boundary, Rustls listener, startup DNS TXT verification, Android Keystore P-256 node identity, foreground service, owner-enabled boot recovery and network-change listener recovery are present in source. Relay node configuration (including host verifiers and route bindings) is encrypted and stored in transactional SQLite; host credential issuance/rotation and individual revocation with durable tombstones are exposed in the owner UI. Visitor path validation and Android credential-store instrumentation tests have been added. These are engineering milestones, not release acceptance. Continuous DNS proof lifecycle revalidation, integrated visitor/owner isolation and live-revocation tests, real-domain ACME issuance/renewal acceptance, Android ABI/package verification and physical/public-network acceptance remain open. Short-lived pairing and the ACME issuance/renewal implementation are present in source.

**Release rule:** no G0–G9 gate is considered PASS based on this plan, a source file, compilation or emulator-only evidence. v0.1.0 remains open until every required gate is evidenced and the owner accepts the physical-device/public-network results.
