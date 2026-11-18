# Pocket Host — Native Android Relay Architecture (v0.1.0)

**Status:** approved architecture; implementation and acceptance are not yet complete.  
**Release:** v0.1.0 — First Hosting  
**Product boundary:** native service bundled in the Pocket Host Android APK.  
**Decision authority:** supersedes earlier Relay deployment notes that described an external terminal-managed Android installation.

## 1. Product decision

Pocket Host Relay is a first-party public visitor gateway implemented natively inside the Pocket Host Android application. It is not a script that users install in a terminal, and it does not depend on an external terminal environment, Node.js, npm, Docker, PostgreSQL or a separately installed Linux userspace.

The Android implementation has two cooperating parts:

- **Kotlin Android layer:** user controls, permissions, foreground-service lifecycle, notifications, boot/restart policy, battery/network state, local diagnostics and Android Keystore integration.
- **Rust Relay engine:** inbound HTTP(S) listener, authenticated outbound host sessions, hostname routing, TLS, bounded request forwarding, persistence and health metrics. The engine is built for the declared Android ABIs and invoked through JNI.

The Relay service is independently startable/stoppable from website hosting and from the fleet control plane. Failure of Relay must not stop local or LAN static website hosting.

## 2. Traffic roles and topology

The Relay supports both directions required by the product:

1. **Inbound visitor traffic:** browsers connect to the Relay's public hostname. The Relay validates the hostname and route, then forwards only the allowed website request to the corresponding authenticated host session.
2. **Outbound host traffic:** a Pocket Host website-host device establishes an authenticated outbound HTTPS/SSE session to the Relay. This allows the website-host phone to remain behind carrier NAT or a restrictive inbound firewall.

A device running the Relay may also host a local website. In that case the local host route must use an explicitly scoped loopback path; it must not bypass visitor authorization or expose the local management API.

### Request path

```text
Browser
  | HTTPS :443 (public edge)
  v
Owner-controlled router / edge forwarding
  | forwards public 443 -> Android app :8444
  v
Pocket Host Android Relay service
  | hostname + route + visitor policy
  v
Authenticated host session (outbound HTTPS/SSE)
  |
  v
Pocket Host host-side RelayAgent
  |
  v
Local Rust website origin
```

The host-side agent continues to use the existing wire contract as the compatibility baseline:

- `GET /v1/device/relay/stream` — authenticated outbound event stream.
- `POST /v1/device/relay/responses/{id}` — authenticated response submission.

The new native gateway must preserve or explicitly version this contract. The visitor listener and host-session endpoints are separate logical surfaces with separate authorization policies.

## 3. Android packaging and runtime

- Package the Rust Relay library in the main Pocket Host APK as native libraries for every ABI declared by the release matrix.
- Extend the existing Gradle `cargo-ndk` pipeline to build the Relay JNI library alongside the existing static-host engine.
- Add a Kotlin Relay foreground service with explicit start, stop, status and restart behavior.
- Use JNI lifecycle calls only; HTTP parsing, TLS, request routing and relay protocol handling remain in Rust.
- Keep the Relay service component isolated from optional Python/Node runtime-worker packages.
- Use Android Keystore-backed keys for node identity and credential protection. The Rust engine must not export private key material into logs or ordinary preferences.
- Request only the Android permissions and foreground-service declarations needed for the documented behavior. Explain notification and battery-optimization implications to the owner.

The minimum supported Android version and ABI list remain those declared by the main app build. A native library missing for a declared ABI is a packaging failure, not a runtime warning.

## 4. Listener ports and public network requirements

Ordinary unrooted Android applications cannot bind privileged TCP ports below 1024. The native Relay must therefore use configurable unprivileged listener ports. The default pair is HTTP 8082 and HTTPS 8444: the existing local website engine already defaults to 8080, and the embedded Python runtime defaults to 8081, so assigning 8080 to a separate Relay listener would create a real bind conflict. Public TCP 80 must be forwarded to 8082 and public TCP 443 to 8444. The defaults remain configurable, but Relay and local hosting ports must be validated as non-overlapping before start.

For conventional public URLs without an explicit port, the owner's network edge must forward public TCP 80 to the app's HTTP port and public TCP 443 to the app's HTTPS port. This can be router port forwarding, an edge firewall/NAT rule or an owner-controlled reverse proxy. For IPv6, the router/firewall must support the equivalent forwarding or proxy arrangement; a globally routed IPv6 address alone does not grant an unprivileged Android process access to port 443.

A public IPv4 path with port forwarding or a globally routed IPv6 path with the required edge forwarding is still necessary. A Relay device behind CGNAT with no globally reachable IPv6 and no inbound forwarding cannot accept unsolicited public visitors. DNS does not bypass CGNAT. Cloudflare Tunnel remains an optional, separate fallback and must continue to work independently.

The app must show the actual bind address/port and distinguish:
- local listener ready,
- host session connected,
- DNS ownership verified,
- TLS certificate ready,
- public visitor acceptance verified.

None of these states may be inferred from another.

## 5. TLS and hostname ownership

- Require a DNS TXT ownership challenge before a hostname can be activated.
- Bind each verified hostname to an explicit site/host route.
- Terminate visitor TLS at the Relay edge using a reviewed Rust TLS implementation and a documented certificate issuance/renewal path.
- The initial v0.1.0 edge contract is valid HTTPS and HTTP-to-HTTPS redirect by default.
- Certificate issuance must account for the public 80/443-to-8082/8444 forwarding requirement. The chosen ACME challenge method and renewal behavior must be tested on the supported network topology before claiming automatic TLS.
- Do not trust arbitrary forwarded client-IP headers. Client address metadata is accepted only from explicitly configured, authenticated/trusted edge proxies; direct connections use the socket peer address.
- Unknown, unverified, expired or revoked hostnames fail closed.

The owner must be able to inspect the hostname, TXT challenge, DNS verification state, certificate state, bound site and revocation state from the app.

## 6. Identity, pairing and credentials

The Relay node has an identity distinct from fleet-control-plane identity and from website-host identity.

- Generate a node identity on the Android device and protect its private key with Android Keystore.
- Pair a host through a short-lived, single-use owner-approved code or QR flow.
- Issue a cryptographically random, high-entropy host-ingress credential scoped to that node and approved host/route.
- Store only a verifier/hash of issued credentials on the Relay node; store the host-side secret encrypted using the host device's Android Keystore.
- Support credential rotation, individual revocation, node reset and host disconnect.
- Never reuse fleet admin tokens, remote-control tokens, Cloudflare tokens or local management API credentials as Relay credentials.
- Persist pairing, route and revocation state across process/device restarts.

## 7. Data plane and isolation

The visitor listener is a website data plane, not an administrative API.

- Public visitor requests may reach only an explicitly verified hostname and its selected website route.
- Public visitors must never reach Android local management APIs, owner settings, pairing, device commands, runtime broker, Workbench drafts, debug endpoints, fleet APIs or MCU controls.
- Host-session endpoints require independent host credentials and are not treated as visitor routes.
- Owner setup and management operations are local-only in v0.1.0, bound to loopback or invoked through the app's existing authenticated local UI/API boundary.
- Reject unknown hosts, malformed paths, traversal, unsupported methods and oversized requests.
- Enforce bounded request body size, response size, queue depth, concurrent sessions, per-host quotas and timeouts. Reuse existing Relay limits only after confirming they fit the Android memory/CPU budget; limits must be explicit and covered by tests.
- v0.1.0 supports bounded HTTP request/response forwarding. Arbitrary TCP tunnelling, generic SOCKS proxying and WebSocket support are not implied.
- Do not forward arbitrary caller-supplied headers. Strip hop-by-hop and untrusted forwarding headers; set canonical host and client metadata at the trusted boundary.

## 8. Persistence and recovery

Use a small embedded durable store suitable for an owner-operated Android service (SQLite is the initial design preference). PostgreSQL and the fleet control plane are not runtime dependencies.

Persist:
- node identity reference and configuration version,
- hostname ownership challenges and verification state,
- host/site/hostname bindings,
- credential verifiers, rotation and revocation state,
- certificate metadata and renewal state,
- audit/operational events needed for recovery.

Do not persist active sockets or pretend that an in-memory queue survives process death. On restart:
1. load and validate durable state;
2. fail closed for expired/revoked credentials and invalid certificates;
3. re-open configured listeners only after owner configuration and network state are valid;
4. require host agents to reconnect and re-authenticate;
5. discard stale in-flight visitor requests and return a bounded failure;
6. report recovery outcome without changing website files or local hosting state.

Database migration must be transactional and recoverable. Corrupt or incompatible state must not silently create an unauthenticated default node.

## 9. Android lifecycle and resilience

- Start only after explicit owner action or a separately enabled auto-start preference.
- Run as a correctly declared foreground service with a persistent notification while public listeners are active.
- Stop listeners and close sessions cleanly when the owner stops the service.
- Reconnect outbound host sessions with bounded exponential backoff and jitter.
- Recover after process death, device restart (only when owner-enabled and Android policy permits), Wi-Fi/mobile transition, temporary DNS failure and network loss.
- Handle Doze, battery restrictions, OEM process management, thermal pressure and low-memory termination honestly. The app cannot promise uninterrupted uptime on every consumer phone.
- Keep local website hosting operational if Relay startup, TLS, DNS, remote host session or public network fails.

## 10. Build and test plan

### Build integration
1. Turn `relay-node/` from a manifest-only scaffold into a Rust library with an Android JNI boundary.
2. Add a `cdylib` target and the required Android-compatible dependencies.
3. Extend Gradle's existing `cargo-ndk` task to build both native engines for all supported ABIs.
4. Add Kotlin JNI declarations and a Relay foreground service.
5. Inspect the produced APK to verify the expected native libraries are present for each ABI.

### Automated tests
- Rust unit tests for hostname normalization, DNS proof, credential verification/revocation, routing, TLS policy, request limits, path/header validation and queue backpressure.
- Protocol tests for SSE reconnect, response correlation, duplicate/stale request handling and credential rotation.
- Persistence tests for migration, crash/restart, corrupted state and interrupted writes.
- JNI/Kotlin tests for start/stop/status, repeated start, clean stop, native crash propagation and service restart.
- Negative security tests proving visitor routes cannot access management or host-control endpoints.
- ABI build tests for every declared Android ABI.

### Physical-device and public-network acceptance
- Install the signed main APK on named physical devices across the declared Android/ABI matrix.
- Start/stop Relay from the app and verify foreground notification and permission behavior.
- Test inbound visitors from mobile data and an independent network through public DNS, edge port forwarding and valid TLS.
- Test a separate website-host Android device behind NAT using the outbound host session.
- Verify correct hostname/site routing, assets, redirects, unknown-host rejection, revocation and management-route denial.
- Kill/restart the process, reboot the device, toggle networks, interrupt host sessions and verify durable recovery.
- Measure CPU, memory, battery and thermal impact during a documented soak period.
- Record device model, Android version, ABI, router/IPv4/IPv6 topology, DNS, certificate state, logs, APK SHA-256 and G0–G9 evidence.

## 11. v0.1.0 scope boundaries

Included: one owner-operated Android Relay node, inbound HTTP(S) visitor handling, outbound authenticated host sessions, one or more explicitly bounded host/site routes, DNS TXT ownership proof, TLS, independent credentials, local owner controls, persistence, restart/reconnect and public-network acceptance.

Excluded: multi-region routing, multi-node scheduling, active-active failover, fleet-wide orchestration, arbitrary TCP forwarding, generic proxy/VPN behavior, WebSocket support unless separately specified and tested, mandatory cloud services, and a separate terminal-managed installation.

## 12. Current implementation status

The repository now contains an initial `relay-node/` Rust library with JNI start/stop/status entry points, a bounded HTTP visitor router, authenticated outbound SSE host sessions, request/response correlation, host/site route validation and initial unit tests. The Gradle build has a separate NDK task for the Relay library. The HTTP-only listener is deliberately restricted to loopback so host credentials cannot be sent over plaintext on a public interface.

Still incomplete: the generated Relay Cargo lockfile must be committed after CI resolution; TLS and certificate lifecycle; DNS TXT ownership verification; Android Keystore-backed configuration and node identity; owner-approved pairing and credential issuance/rotation/revocation; durable standalone route state; dedicated Kotlin foreground service and owner controls; public visitor acceptance; and physical-device/recovery testing. The existing website engine owns port 8080 (Python defaults to 8081); Relay defaults remain HTTP 8082 and HTTPS 8444.

The execution checklist is maintained in [RELAY_IMPLEMENTATION_PLAN.md](RELAY_IMPLEMENTATION_PLAN.md). This document remains an approved architecture, not evidence that Relay or v0.1.0 is complete. Do not close any release gate until source, automated tests, physical-device results and external-network evidence exist.
