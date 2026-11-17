# Pocket Host — Public Ingress v0.1.0

**Release assignment:** v0.1.0 — First Hosting  
**Status:** mandatory target; current source is only partially integrated and has not passed public-domain acceptance.

## 1. User outcome

An owner imports a static website into the Android host and makes it reachable from ordinary Internet-connected browsers using the owner's domain. v0.1.0 has two public-ingress paths:

1. **Cloudflare Tunnel:** optional third-party tunnel integration using an owner-managed Cloudflare account and named tunnel.
2. **AssayFlux Relay:** first-party, owner-operated relay gateway. It must be separately installable on owner-controlled hardware, including Termux/Android and Linux targets. A paid VPS, Cloudflare account or other managed relay provider must not be a product prerequisite.

The Android website host may sit behind mobile carrier NAT. It maintains an outbound authenticated connection to the owner-operated Relay Node. Visitors connect to the Relay Node's public hostname; the gateway forwards bounded website requests over that established connection.

Local preview and LAN hosting must continue if either public provider or the Internet is unavailable.

## 2. Public reachability is still required

“No VPS required” does **not** mean “no publicly reachable endpoint required.” The owner-operated Relay Node must be reachable from the Internet through one of these arrangements:

- Public IPv4 address with router port forwarding for TCP 80/443.
- Globally routed IPv6 address with firewall rules permitting the required inbound ports.
- Another owner-controlled always-on device/network that has one of the above.

The owner's DNS A/AAAA records must resolve to that reachable node. Dynamic IPv4 may use a user-selected DDNS mechanism. If the only available network is behind carrier-grade NAT (CGNAT), has no global IPv6 and offers no inbound port forwarding, that device cannot be the public gateway. A different reachable owner-owned node or the optional Cloudflare Tunnel is then required. DNS records alone cannot bypass CGNAT.

A phone hosting the website may remain behind CGNAT because it connects outbound to the Relay Node. The Relay Node itself cannot be behind an unreachable NAT if it is expected to accept visitor connections.

## 3. HTTP and HTTPS contract

Target behavior for each verified public hostname:

- HTTPS presents a valid certificate for that hostname.
- HTTP redirects to HTTPS by default (301/308).
- Plain HTTP content serving is an explicit owner opt-in with an insecure-transport warning.
- TLS terminates at the selected public edge. The Android website origin remains loopback HTTP unless a separately reviewed origin-TLS mode is added.
- A local HTTP listener or connector-ready status is not evidence of public HTTPS.

**Current-source distinction:** the existing AssayFlux Relay Caddyfile redirects port 80 to HTTPS and the Relay visitor handler rejects requests without a trusted HTTPS frontend. There is no plain-HTTP content opt-in in the current Relay code. Cloudflare's HTTP/HTTPS behavior is configured in the owner's Cloudflare zone; Pocket Host does not currently manage that policy.

Never expose the Android management API, device enrollment, runtime broker, Workbench drafts, debug endpoints or MCU controls through a visitor hostname.

## 4. Provider selection and DNS

A hostname is assigned to one provider at a time. Separate hostnames may use separate providers. Enabling both providers does not create automatic same-host active-active failover; that requires an explicit DNS/load-balancer design.

### Cloudflare Tunnel

- Owner creates or reuses a named tunnel in their Cloudflare account.
- Owner maps the public hostname to the Android Rust origin, normally `http://127.0.0.1:8080` (or the configured static-server port).
- Pocket Host stores the tunnel token encrypted with Android Keystore.
- The owner configures DNS, hostname routing, edge TLS and HTTP redirect policy in Cloudflare.
- A successful local cloudflared readiness probe does not prove the public hostname is configured correctly.

### AssayFlux Relay Node

Target v0.1.0 contract:

- Standalone Relay Node installable on Termux/Android and Linux; no Docker or PostgreSQL requirement.
- Separate from the full fleet control plane and its owner/admin APIs.
- Owner-controlled listener on public TCP 80/443, or another explicitly configured public ingress arrangement.
- Owner proves domain control, binds hostname → site → host identity, and can revoke that binding.
- Website host establishes an outbound authenticated HTTPS stream; it never accepts unsolicited Internet connections.
- Relay forwards only bounded HTTP requests to the registered website origin, never arbitrary local ports.
- Relay credentials are dedicated to visitor ingress and are not the fleet-control device token.
- Visitor routes cannot reach management, runtime, device or debug endpoints.

The persistence format, certificate provisioning, local setup API and Termux service lifecycle must be frozen at G0/G1 before implementation.

## 5. What the repository currently contains

### Present source foundations

- Android Cloudflare token store, cloudflared process runner, readiness probe and foreground-service reconnect supervisor.
- Android RelayAgent using outbound HTTPS/SSE and bounded local HTTP forwarding.
- Rust Relay visitor handler, DNS TXT hostname verification, route registration and a separate visitor listener.
- PostgreSQL migration for Relay hostname/site records.
- Caddy reverse-proxy/TLS configuration.
- Browser-console Relay hostname registration, verification and deletion panel.

### Current gaps — do not describe as completed

- RelayAgent gets its endpoint and device token from RemoteControlAgent and starts only when remote-control configuration is present.
- Relay registration currently uses registered fleet devices and the fleet owner/admin API.
- Relay gateway and full fleet API share the same Rust executable; startup requires PostgreSQL, a control API hostname and administrator credentials.
- Relay sessions and pending visitor requests are held in an in-memory RelayHub; they are lost on process restart.
- The current Compose deployment is Docker/PostgreSQL/Caddy-oriented.
- No standalone Termux Relay Node package, installer, independent credential flow or no-PostgreSQL deployment exists.
- No independent public DNS/TLS/content acceptance is recorded.
- HTTP-to-HTTPS redirect exists at the current Relay edge; optional plain HTTP serving does not.

These source foundations must be decoupled and packaged into the standalone owner-operated Relay Node before the first-party path can be called independently self-hosted.

## 6. Current Relay transport limits

Current source-level limits include:

- Request and response bodies: 8 MiB maximum.
- Gateway request deadline: 45 seconds.
- Android local request workers: 4 with a bounded queue.
- Gateway pending requests: 32 global and 8 per device.
- Supported methods: GET, HEAD, POST, PUT, DELETE and OPTIONS.
- No general TCP, WebSocket or unlimited streaming support.

The target standalone Relay may retain or revise these limits only through explicit tests and documented resource budgets.

## 7. Mandatory v0.1.0 acceptance

| Gate | Required evidence |
|---|---|
| G0 | Freeze domain/provider/HTTP/HTTPS behavior, public network topology, Termux/Linux target matrix, DNS, TLS and CGNAT guidance |
| G1 | Threat model for domain proof, host credentials, TLS, visitor/admin isolation, abuse controls, NAT boundary and owner data |
| G2 | Cloudflare integration plus standalone Relay Node, independent host credentials, hostname binding/revocation, Termux service lifecycle and safe request routing |
| G3 | Tests for domain proof, route ownership, credential rotation/revocation, request limits, timeouts, redirects and negative cases |
| G4 | Main APK plus versioned Relay Node artifact(s) for declared Termux/Linux targets, with hashes and provenance |
| G5 | Fresh install/upgrade and service lifecycle on named Android/Termux/Linux targets |
| G6 | Owner's real hostname reaches expected website from an independent network through each enabled provider; valid HTTPS, HTTP redirect, correct content and management-route denial |
| G7 | Screen-off/Doze where applicable, network transitions, CGNAT guidance, gateway restart, host reconnect and provider-failure isolation |
| G8 | No secret leakage, verified domain ownership, valid TLS, visitor-only routes, bounded resource use and abuse handling |
| G9 | APK/Relay installation guide, DNS/TLS and router/IPv6 instructions, CGNAT troubleshooting, known limits, artifact hashes and owner sign-off |

No v0.1.0 public-hosting claim is allowed without reproducible external-network evidence.

## 8. Owner-provided prerequisites

- A registered domain and permission to edit its DNS records.
- Cloudflare account and named-tunnel token only if Cloudflare ingress is selected.
- For self-hosted Relay: an owner-controlled device/network with public IPv4 port forwarding or globally routed IPv6, DNS A/AAAA records and inbound 80/443 access.
- The website source or built static output. For React/Vite, import the production `dist/` contents; v0.1.0 does not build raw React/TypeScript source on the phone.
