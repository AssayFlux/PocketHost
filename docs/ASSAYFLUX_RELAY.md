# AssayFlux Relay — Owner-Operated Visitor Gateway

**Release assignment:** v0.1.0 — First Hosting  
**Current source status:** partial Relay visitor path exists; standalone owner-operated Relay Node is not yet implemented or publicly accepted.

## Purpose

AssayFlux Relay is the first-party public visitor gateway for Pocket Host. It lets an Android website host remain behind carrier NAT while a separately operated Relay Node accepts browser traffic on the owner's public domain and forwards bounded HTTP requests over an authenticated outbound host connection.

The Relay Node is intended to run on owner-controlled hardware, including Termux/Android or Linux. A paid VPS is not required. A public network path is required.

## No-VPS operating model

The owner may run the Relay Node on a spare Android device using Termux, a Linux home server, a mini PC or another supported always-on device.

The node must have at least one publicly reachable ingress path:

- Public IPv4 with router port forwarding for TCP 80 and 443, or
- Globally routed IPv6 with firewall rules allowing the required inbound ports.

DNS A/AAAA records must resolve to the reachable node. If the domain's DNS is hosted by Cloudflare but the owner wants a direct first-party Relay path, configure the hostname as DNS-only; enabling Cloudflare's orange-cloud proxy keeps Cloudflare in the visitor request path and is no longer a direct-only Relay route. If the device is behind CGNAT and has no global IPv6 or port-forwardable address, it cannot accept public browser traffic. In that case use a different owner-controlled reachable device/network or choose Cloudflare Tunnel as an optional third-party fallback. This is a network constraint; Pocket Host cannot bypass it by changing DNS.

## Target architecture

```text
Browser on Internet
        |
        | HTTPS / HTTP→HTTPS
        v
Owner's public AssayFlux Relay Node
  - domain / TLS edge
  - verified hostname → site → host route
  - bounded HTTP request queue
        |
        | authenticated outbound stream
        v
Pocket Host Android website host
  - RelayAgent
  - loopback Rust website engine
```

The public visitor listener and the owner/admin control interface must be separate. The visitor listener must never proxy arbitrary ports or expose management, runtime, device, Workbench, debug or MCU endpoints.

## HTTP and HTTPS

Target behavior:

- HTTPS with a valid certificate for the registered hostname.
- HTTP redirects to HTTPS by default.
- Plain HTTP content serving is optional, explicitly enabled and warned about.
- TLS terminates at the Relay Node; the Android origin remains local HTTP.
- The Relay Node must not claim a working public hostname until DNS, TLS and actual visitor content are externally verified.

The current source Caddyfile redirects port 80 to HTTPS, and the current Relay visitor handler requires a trusted HTTPS frontend. There is no configurable plain-HTTP serving mode yet.

## Current repository implementation

### Android host

`app/src/main/java/com/assayflux/pockethost/management/RelayAgent.kt` contains an outbound HTTPS/SSE client. It receives visitor requests, validates bounded fields, forwards to the local Rust website engine and posts the response to the gateway. It does not open an Internet-facing listener.

Important coupling: `RelayAgent.start()` checks `RemoteControlAgent.isConfigured()`, takes the endpoint from `RemoteControlAgent.endpoint()` and takes the Relay device token from the remote-control credential store. `RemoteControlAgent.start()` starts RelayAgent. Therefore the current Relay client is not an independent website-hosting provider.

### Existing gateway

- `control-plane/src/main.rs` implements hostname registration, DNS TXT verification, TLS authorization check, visitor routing and device SSE/response endpoints.
- `control-plane/src/relay.rs` implements the in-memory active-session and pending-request hub.
- PostgreSQL stores devices and verified hostname/site bindings.
- The same Rust executable contains both fleet admin/device API and Relay visitor API. It requires PostgreSQL, `DATABASE_URL`, an admin token and a control-plane hostname.
- It binds separate control and visitor listeners (defaults 8080 and 8081); Caddy publishes ports 80/443 and performs on-demand TLS for verified Relay hostnames.
- `control-plane/compose.yaml` currently uses PostgreSQL, Docker Compose and Caddy. This is not a standalone Termux installation.
- `RelayHub` is in memory. Active streams and pending requests are lost on gateway restart and must reconnect/recover.
- `web-console/src/ui/RemoteControlPlane.tsx` contains a browser UI for hostname registration, DNS TXT proof, verification and deletion. The Android app does not currently expose an independent Relay setup flow.

### Current transport limits

- HTTP request/response only; not a general TCP or WebSocket tunnel.
- Methods: GET, HEAD, POST, PUT, DELETE and OPTIONS.
- Body maximum: 8 MiB in each direction.
- Gateway timeout: 45 seconds.
- Android local forwarding uses four request workers and a bounded queue.
- Gateway pending requests are capped at 32 globally and 8 per device.

These are current source-level limits, not tested production throughput guarantees.

## v0.1.0 implementation work

1. Extract a standalone Relay Node from the fleet control-plane executable.
2. Remove mandatory dependency on fleet device registration and fleet-control bearer credentials. Create dedicated host identity, pairing and revocable visitor-ingress credentials.
3. Provide a minimal owner setup flow for Termux/Linux: install, start/stop, status, domain registration, DNS TXT proof, TLS, route binding and revocation.
4. Avoid mandatory Docker and PostgreSQL for the standalone profile. Use a small durable local store and bounded in-memory request state with safe restart recovery.
5. Make the Android Relay connector configurable independently of RemoteControlAgent and the fleet control plane.
6. Preserve strict visitor/admin separation and only forward to a registered site origin.
7. Add public IPv4/IPv6, DNS, TLS, CGNAT, restart, reconnect and external-network tests.
8. Document that a publicly reachable owner-controlled network endpoint is mandatory; a paid VPS is not.

## Security and privacy

- Verify domain ownership before issuing public TLS or forwarding visitor traffic.
- Use separate, revocable host-ingress credentials; never reuse admin tokens or fleet device tokens.
- Keep secrets out of argv, logs, diagnostics and backups.
- Validate hostname, site, method, path, headers, body size, timeout, concurrency and response framing.
- Never accept an arbitrary local target URL or port from a visitor.
- Rate-limit abusive visitors and bound memory, queues, file descriptors and request time.
- Do not log raw visitor bodies or customer site content by default.
- TLS termination means the Relay operator can inspect visitor HTTP content. State this clearly to the owner.
- Revocation must stop new requests and cancel pending requests for that hostname/site.

## Acceptance

AssayFlux Relay is not complete until a clean Termux/Linux installation on an owner-controlled publicly reachable device can register and verify a real domain, obtain/serve valid HTTPS, redirect HTTP, receive visitors from mobile data, forward to a separate Pocket Host Android device behind NAT, survive gateway/host reconnects, revoke the hostname and prove that no management route is exposed.
