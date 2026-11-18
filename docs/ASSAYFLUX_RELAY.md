# AssayFlux Relay — Native Android Public Visitor Gateway

**Release:** v0.1.0 — First Hosting  
**Architecture:** Kotlin Android foreground service + Rust/Tokio engine through JNI, bundled in the Pocket Host APK.  
**Authoritative design:** [Native Android Relay Architecture](NATIVE_ANDROID_RELAY.md)  
**Decision:** [ADR-020](DECISIONS.md#adr-020--native-android-relay-service-replaces-external-terminal-deployment)

## Purpose

AssayFlux Relay is Pocket Host's first-party, owner-operated public visitor gateway. The native Android Relay service supports both:

- **Inbound:** accept public browser HTTP(S) requests for owner-verified hostnames and forward only the approved website route.
- **Outbound:** accept authenticated, persistent host sessions initiated by Pocket Host website-host devices. A website-host phone can remain behind carrier NAT because it initiates the connection.

The Relay is bundled in the main Android APK. Kotlin owns Android service lifecycle and user-facing controls. Rust owns network listeners, TLS, hostname routing, host-session protocol, bounded request forwarding and durable state. The Relay does not depend on the fleet control plane.

## Current source status

The Android host-side `RelayAgent` already implements an outbound HTTPS/SSE request/response client. Its dedicated `RelayCredentialStore` encrypts host credentials with Android Keystore AES-GCM. `HostingService` starts the host-side agent independently of remote-control configuration.

The existing visitor gateway remains in `control-plane/`, shares the fleet control-plane executable and PostgreSQL, and uses an in-memory `RelayHub`. The `relay-node/` directory contains only a Cargo manifest. The following v0.1.0 work is not yet implemented:

- Rust Relay gateway library and Android JNI bridge.
- Kotlin Relay foreground service and owner-facing lifecycle/status controls.
- Native inbound visitor listener and TLS/certificate lifecycle.
- Independent node identity and node-side host credential issuance, rotation and revocation.
- Durable hostname/site/host bindings and restart recovery.
- Public network and physical-device acceptance.

Do not describe the native Relay as implemented until these items and the release evidence are complete.

## Native component boundaries

| Component | Responsibility |
|---|---|
| Kotlin Relay foreground service | Start/stop/status, notification, Android permissions, network changes, recovery and owner controls |
| Rust Relay engine | Inbound visitor listener, outbound host sessions, TLS, route validation, request forwarding, persistence and limits |
| Android Keystore | Node identity protection and host-side credential encryption |
| Embedded durable store | Hostname proofs, route bindings, credential verifiers, revocations and certificate metadata |
| Existing RelayAgent | Outbound authenticated SSE session from a website-host device; preserve the current wire contract or version it deliberately |
| Existing Rust website engine | Serve the selected local site; remain independent of Relay availability |

The native Relay library must be built for every ABI declared by the main APK and packaged alongside the existing Rust static-host library. Gradle's current `cargo-ndk` pipeline must be extended; the Relay is not a separate terminal-managed install or APK.

## Network and public endpoint requirements

An ordinary unrooted Android app cannot bind TCP ports below 1024. The native listeners use configurable unprivileged ports with defaults HTTP 8082 and HTTPS 8444. These defaults avoid a known collision: the existing local website engine uses 8080 and the embedded Python runtime defaults to 8081. For conventional public URLs, the owner's router/firewall/reverse proxy must forward public TCP 80 to 8082 and public TCP 443 to 8444. Relay must reject a configuration that overlaps an active local-host listener. IPv6 requires equivalent edge forwarding or proxying; a global IPv6 address alone does not let an ordinary app bind port 443.

The Relay device needs a publicly reachable IPv4 path with port forwarding or globally routed IPv6 with the required edge forwarding. CGNAT without global IPv6 or inbound forwarding cannot accept public visitors. DNS does not bypass CGNAT. Cloudflare Tunnel remains an optional, independent fallback.

## Hostname, TLS and security contract

- Require DNS TXT proof before activating a public hostname.
- Bind each verified hostname to an explicit site and authenticated host session.
- Serve valid HTTPS and redirect HTTP to HTTPS by default.
- Define and test certificate issuance and renewal against the public edge forwarding topology before claiming automated TLS.
- Keep owner management and pairing local-only in v0.1.0.
- Use independent high-entropy, scoped, revocable Relay credentials; never reuse fleet admin, RemoteControlAgent or Cloudflare tokens.
- Store credential verifiers on the Relay node and protect host-side secrets with Android Keystore.
- Public visitor routes may reach only verified website routes. They must never reach owner settings, pairing, device commands, local management APIs, runtime brokers, Workbench drafts, fleet APIs or MCU controls.
- Fail closed for unknown/revoked hostnames, invalid credentials, expired certificates and invalid route state.
- Bound request size, response size, concurrency, pending queues, timeouts and per-host resource use.
- v0.1.0 is bounded HTTP forwarding, not arbitrary TCP, generic proxy/VPN or WebSocket support.

## Existing host-session wire contract

The current Android client uses:

- `GET /v1/device/relay/stream` — Bearer host credential, Server-Sent Events.
- `POST /v1/device/relay/responses/{id}` — same credential, JSON response.

The native gateway must preserve this contract or update the Android client and protocol tests in the same reviewed change. It must authenticate every stream and response, handle reconnects and reject stale/duplicate request IDs.

## Implementation sequence

1. Implement the Rust Relay engine in `relay-node/` as an Android-compatible native library with a JNI lifecycle boundary.
2. Extend Gradle's NDK build to produce Relay libraries for all supported ABIs and verify APK packaging.
3. Implement Kotlin foreground-service lifecycle and owner-facing enable/start/stop/status/health/recovery controls.
4. Implement node identity, short-lived owner-approved host pairing, independent credential issuance/rotation/revocation and durable route state.
5. Implement DNS TXT hostname proof, TLS certificate issuance/renewal, inbound visitor listener and strict visitor/host/owner route separation.
6. Connect inbound visitor requests to authenticated outbound host sessions and the local Rust website origin with bounded queues, body sizes, concurrency and timeouts.
7. Add Rust, protocol, persistence, JNI, Kotlin service and security-negative tests.
8. Complete named physical-device and independent public-network acceptance, including port-forwarding topology, DNS, TLS, visitor content, revocation and restart recovery.

## Acceptance

The native Relay is not accepted until a signed main APK runs the service on named physical Android devices; the node registers and verifies a real hostname; public visitors from an independent network receive the correct site over valid HTTPS; a separate host phone behind NAT connects outbound and serves requests; HTTP redirects correctly; unknown/revoked hosts fail closed; no management route is reachable; and process death, reboot, network changes and host reconnect recover without corrupting local hosting.

Record source SHA, APK SHA-256, device model, Android version, ABI, edge forwarding configuration, public DNS/TLS observations, logs, resource/soak results and G0–G9 evidence. A design document or CI compile does not close physical-device or public-network gates.

Linux-native Relay packaging remains a v0.1.1 deliverable. Multi-node scheduling, regional scale and advanced failover remain v0.1.2.
