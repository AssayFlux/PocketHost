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
- [x] Add owner-enabled boot recovery for the native Relay service when encrypted Relay configuration exists.\n- [ ] Add network-change recovery and verify failure isolation from local hosting and Cloudflare Tunnel.
- [ ] Add service/JNI instrumentation tests and notification/permission tests.

### R2 — TLS, hostname proof and route lifecycle

- [x] Select Rustls with the ring crypto provider and add a native HTTPS listener; Android ABI verification remains pending.
- [ ] Implement certificate/key generation, automatic ACME issuance/renewal and expiry monitoring. The current engineering import accepts an owner-supplied PEM certificate/key.
- [x] Require a DNS TXT ownership challenge at startup before activating configured host routes.
- [ ] Persist hostname/site/host binding and verification/revocation state in durable standalone storage; the current encrypted configuration is a temporary persistence layer.
- [ ] Keep unknown, unverified, expired and revoked hostnames fail-closed; DNS TXT is currently rechecked at startup, while certificate expiry/revocation lifecycle remains open.
- [x] Redirect HTTP to HTTPS when TLS is configured.
- [x] Keep plaintext host-session authentication disabled on public interfaces; public binding requires TLS material and verified DNS TXT routes.

### R3 — Independent identity, pairing and credentials

- [x] Generate a stable P-256 node identity protected by Android Keystore; expose only its public identity and domain-separated challenge signatures.
- [ ] Implement short-lived, single-use, owner-approved pairing.
- [ ] Issue independent high-entropy host ingress credentials; store only verifiers on the Relay node.
- [ ] Store host-side credentials encrypted by that host device's Android Keystore.
- [ ] Implement rotation, individual revocation, node reset and disconnect.
- [ ] Persist state transactionally and recover fail-closed after corruption or interrupted writes.

### R4 — End-to-end integration and hardening

- [x] Implement the visitor-to-authenticated-outbound-SSE host-session request/response path using the existing host-side RelayAgent wire contract.
- [x] Enforce initial request/response body, queue, concurrency and timeout limits.
- [x] Strip untrusted request headers and allow-list response headers.
- [x] Add native visitor-path rejection tests for Relay control namespaces, traversal, encoded separators/dots, double-encoding, malformed escapes and repeated slashes.\n- [ ] Verify visitor routes cannot reach owner management, pairing, device-control, Workbench or runtime APIs with integrated automated negative tests.
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

The Rust gateway and JNI lifecycle boundary, Rustls HTTPS listener, DNS TXT startup verification, Android Keystore-backed encrypted configuration, P-256 node identity, foreground service, initial owner controls and owner-enabled boot recovery are present in source. Visitor path validation now rejects reserved control namespaces and common encoded/double-encoded traversal forms. These are engineering milestones, not release acceptance. Owner-approved pairing, credential issuance/rotation/revocation, ACME issuance/renewal, durable transactional route/revocation state, continuous DNS proof lifecycle, network-change recovery, integrated isolation/lifecycle tests, ABI/APK verification and physical/public-network acceptance remain open.

**Release rule:** no G0–G9 gate is considered PASS based on this plan, a source file, compilation or emulator-only evidence. v0.1.0 remains open until every required gate is evidenced and the owner accepts the physical-device/public-network results.
