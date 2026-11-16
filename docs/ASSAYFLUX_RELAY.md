# ASSAYFLUX RELAY — PRODUCT AND ENGINEERING SPECIFICATION

**Parent product:** AssayFlux Pocket Host  
**Status:** planned; not implemented or deployed  
**Priority:** Gate 3 in `docs/NORTH_STAR.md`  
**Purpose:** first-party outbound tunnel and visitor-access gateway for phone-hosted websites.

## 1. Product objective

Let a Pocket Host owner publish a website without Cloudflare and without exposing the phone's public IP address to ordinary visitors. The phone initiates an authenticated outbound connection to AssayFlux Relay. Visitors connect to a public relay hostname; the relay forwards authorized streams to the phone.

This hides the phone IP from ordinary visitors, not from the relay operator, carrier or network provider. Disclose this plainly.

## 2. Non-goals

- Not a replacement for Rust website serving on the phone.
- Not a remote shell or arbitrary command channel.
- Not a public management API.
- Not an uptime guarantee or unlimited-bandwidth service.
- Not custom cryptography.
- Not a prerequisite for local hosting or Cloudflare Tunnel.
- Not a reason to route management endpoints through the public gateway.

## 3. Architecture

```text
Visitor → public TLS gateway → hostname and visitor policy
        → enrolled device tunnel session → multiplexed stream
        → Android Relay Agent → Pocket Host Rust listener
        → site router → static content / isolated runtime
```

### Services
1. **Relay Gateway:** TLS, host validation, request limits, visitor authorization and stream routing.
2. **Device Enrollment Broker:** one-time pairing, device identity, mutual authentication, key rotation and revocation.
3. **Android Relay Agent:** outbound connection, heartbeat, reconnect, network-change detection and local health reporting.
4. **Stream Multiplexer:** concurrent streams, flow control, bounded buffers, deadlines and cancellation.
5. **Domain Registry:** prove hostname control and bind hostname → device → site.
6. **Certificate Manager:** issue, renew and revoke TLS certificates for verified hostnames.
7. **Visitor Access Gateway:** Public, Private and Restricted site policies.
8. **Operations Plane:** metrics, abuse detection, quotas, audit, incident controls and support tooling.

## 4. Transport decision

Initial implementation should use mature TLS-protected HTTP/2 streams or a reviewed reverse-tunnel protocol. WebSocket/TLS is a compatibility fallback, not the preferred multiplexing core. Evaluate QUIC/HTTP/3 after the TCP path is stable and tested. Never design a new cryptographic handshake.

Requirements:
- Mutual device authentication or equivalently reviewed proof of possession.
- Short-lived access credentials and immediate revocation.
- TLS 1.2+ with modern cipher policy; prefer TLS 1.3 where available.
- Per-device and per-site stream limits.
- Bounded frame, header, body, queue and buffer sizes.
- Flow control, backpressure, deadlines and cancellation.
- Exponential reconnect with jitter and bounded retry.
- Replay-resistant typed commands with explicit scope and expiry.
- No arbitrary filesystem, shell or management command execution.

## 5. Identity and authorization

### Device enrollment
1. Owner creates a pairing request in Pocket Host.
2. Relay issues a short-lived one-time enrollment challenge.
3. Owner approves the exact device and requested capabilities.
4. Device proves possession of a device-bound key.
5. Relay binds device identity to account and allowed hostnames.
6. Owner can revoke device; gateway closes active sessions and rejects new ones.

Store private keys in Android Keystore where supported. Do not export private keys in backups. Handle key invalidation and device replacement through explicit re-enrollment.

### Hostname ownership
- Require DNS TXT or equivalent domain-control proof before activating a custom hostname.
- Prevent hostname takeover and duplicate active ownership.
- Bind each hostname to an owner, device and site ID.
- Validate Host/SNI consistency and reject unknown or conflicting routes.

### Visitor access modes
- Public: intentional anonymous access.
- Private: authenticate visitor before forwarding.
- Restricted: allow only configured users/groups/invitation grants.
- Use short-lived, site-scoped, audience-bound tokens.
- Enforce revocation and rate limits before the phone receives traffic.

Owner/Operator identity is a separate security plane. Visitor tokens never authorize device management.

## 6. TLS and privacy modes

**Standard mode (initial):** TLS terminates at gateway. Gateway can inspect HTTP content and metadata; this must be disclosed. The gateway forwards traffic over a separately authenticated encrypted device tunnel.

**End-to-end TLS pass-through (later):** website TLS terminates on the phone. Relay sees routing metadata required to select a tunnel but cannot read HTTP content. Certificate issuance, SNI routing and visitor authentication need a separate design review.

Do not describe standard gateway termination as end-to-end encrypted from visitor to phone.

## 7. Reliability and resource limits

- Detect network changes and reconnect without requiring app reinstall.
- Heartbeat and session lease; stale devices are removed.
- Preserve active stream semantics where protocol permits; otherwise fail requests predictably.
- Apply per-device/site concurrency, bandwidth and request limits.
- Avoid unbounded memory or request queues.
- Prioritize health/control traffic without starving website streams.
- Relay outage must not stop local hosting or the Cloudflare connector.
- Phone battery/Doze/OEM limits must be visible; do not promise permanent background connectivity.

## 8. Security threat model

Protect against device impersonation, stolen tokens, replay, hostname takeover, cross-tenant stream routing, unauthorized visitor access, request smuggling, malformed frames, relay abuse, denial of service and management-plane exposure.

Acceptance tests:
- Unenrolled device cannot create a tunnel.
- Revoked device loses existing and future sessions.
- Expired/replayed enrollment and visitor tokens fail.
- Hostname cannot be claimed by another owner.
- Cross-device/site stream injection fails.
- Unknown Host/SNI, malformed headers and oversized frames fail safely.
- Relay cannot invoke arbitrary shell or filesystem operations.
- Management API is not reachable through any public hostname.
- Backpressure and overload remain bounded.
- Network transitions recover and stale sessions expire.
- Relay logs redact tokens and sensitive content.

## 9. Deployment model

Start with one independently deployable gateway region and a separate control/identity store. Keep gateway stateless where practical; use durable device/hostname state and short-lived session leases. Add multi-region routing only after single-region security, capacity, observability, backup and incident response are proven.

Secrets must live in a managed secret store, not source or container images. Define retention, abuse response, domain verification, rate policy, bandwidth accounting and privacy policy before public beta.

## 10. Implementation phases

1. Threat model, protocol selection and domain/device identity schema.
2. Gateway TLS listener and authenticated device registration.
3. Android outbound agent and heartbeat/reconnect.
4. One-site stream forwarding to Rust listener.
5. Multiplexing, backpressure, rate and resource limits.
6. Hostname proof, certificate lifecycle and site registry.
7. Visitor Public/Private/Restricted policies.
8. Revocation, abuse prevention, audit, metrics and recovery.
9. API 26/33/35 plus physical-device and external-network acceptance.
10. Independent security review and limited beta.

## 11. Exit criteria

AssayFlux Relay is not complete until an enrolled phone can establish an outbound-only authenticated tunnel; external visitors can reach the intended site through a verified hostname; the phone's public IP is not exposed to ordinary visitors; revoked devices and unauthorized visitors are denied; network changes recover; management endpoints remain private; limits are enforced; and independent review accepts the design.

Cloudflare Tunnel and Local Network modes must continue working independently.
