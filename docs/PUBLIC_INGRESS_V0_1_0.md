# Pocket Host — Public Ingress v0.1.0

**Release assignment:** v0.1.0 — First Hosting  
**Status:** mandatory release scope; implementation and external acceptance remain open.

## 1. User outcome

An owner can import a static website into the Android phone and make it reachable from any Internet-connected device through a custom domain. Pocket Host provides two independent public-ingress options:

1. **Cloudflare Tunnel** — use an owner-managed Cloudflare zone and named tunnel.
2. **AssayFlux Relay** — use the first-party visitor gateway deployed on an owner-controlled public VPS/server.

Local preview and LAN hosting continue to work when either provider, the Internet connection or the relay VPS is unavailable.

## 2. HTTP and HTTPS contract

- Each verified public hostname accepts HTTP and HTTPS.
- HTTPS must present a valid certificate for the exact hostname.
- HTTP redirects to HTTPS by default (301/308). The owner may explicitly enable plain HTTP content serving after an insecure-transport warning.
- Public TLS terminates at Cloudflare or the AssayFlux Relay edge. The Android website origin remains a loopback HTTP listener unless a separately tested origin-TLS mode is introduced.
- Do not claim HTTPS merely because the local Rust server is listening on HTTP.
- Never expose the Android management API, device enrollment, runtime broker, Workbench draft files, debug endpoints or MCU controls through a visitor hostname.

## 3. Provider selection and DNS

A hostname is bound to one provider at a time. The owner may use separate names (for example, `www` through Cloudflare and `relay` through AssayFlux Relay). Pointing the same hostname at both providers simultaneously requires a separately configured DNS/load-balancer failover layer; Pocket Host must not imply automatic active-active routing by simply enabling both connectors.

### Cloudflare Tunnel

- The owner creates/reuses a named tunnel in their Cloudflare account.
- The owner configures the public hostname route to the local Rust origin, normally `http://127.0.0.1:8080` (or the configured website port).
- Pocket Host stores the tunnel token encrypted using Android Keystore; the token is never placed in command arguments, logs, backups or diagnostics.
- Cloudflare edge DNS/TLS and public reachability must be verified independently.
- A connector-ready state is not the same as a working public hostname.

### AssayFlux Relay

- The owner deploys the first-party gateway on a public VPS/server with stable DNS and public TCP 80/443.
- The gateway uses the repository's separate visitor listener and TLS reverse proxy; management endpoints remain restricted to the owner.
- The owner proves hostname ownership with the Relay DNS TXT challenge.
- The hostname is bound to an owner, authenticated host identity and Pocket Host site ID.
- The Android host establishes an outbound authenticated HTTPS/SSE stream. It does not open an unsolicited Internet-facing listener.
- The gateway forwards only bounded HTTP requests to the registered site. It never forwards arbitrary local ports.
- Relay host credentials are visitor-ingress credentials, distinct from owner/admin credentials. Revoke them independently.
- The current transport is bounded HTTP request/response, not general TCP, WebSocket or unlimited streaming.

## 4. Current source foundations and gaps

The repository already contains:
- Android `cloudflared` process lifecycle, loopback readiness probe and reconnect supervisor.
- Cloudflare token encryption using AES-256-GCM and Android Keystore.
- Android `RelayAgent` outbound SSE stream, bounded request/response forwarding and reconnect behavior.
- Rust Relay visitor gateway, DNS TXT hostname verification, route binding and a separate visitor listener.
- Caddy reverse-proxy configuration for TLS at the Relay edge.

These are source foundations, not public-hosting acceptance. The current Relay agent is started through the remote-control agent configuration and uses its relay device token. v0.1.0 must verify or decouple this configuration so the website owner can use Relay without enabling the full fleet administration feature set. The Relay gateway also needs a real owner-controlled VPS deployment, DNS/TLS setup and operational monitoring.

## 5. Limits and operating behavior

Current Relay transport limits documented by source include:
- Request/response body: 8 MiB maximum.
- Request deadline: 45 seconds.
- Four concurrent local requests per device, with bounded queueing.
- Supported methods: GET, HEAD, POST, PUT, DELETE and OPTIONS.
- No general-purpose TCP or WebSocket forwarding.

Cloudflare and Relay must expose independent configured/connecting/ready/error states, start/stop controls, reconnect behavior and useful redacted diagnostics. Provider failure must not stop local hosting or the other provider.

Android battery management, Doze, mobile data limits, carrier outages and device thermal state can interrupt hosting. Do not promise 24/7 uptime on every phone.

## 6. Mandatory v0.1.0 acceptance

| Gate | Required evidence |
|---|---|
| G0 | Frozen domain/provider/HTTP/HTTPS behavior, supported Android ABI/device matrix, DNS topology and relay deployment target |
| G1 | Threat model for domain proof, credentials, TLS termination, public routes, abuse limits and management isolation |
| G2 | Complete Cloudflare and Relay configuration, hostname binding, start/stop, reconnect, status and safe route mapping |
| G3 | Unit/contract tests for route validation, DNS proof, token handling, HTTP forwarding limits, redirects and negative cases |
| G4 | Main APK with required cloudflared ABIs and deployable Relay gateway image, signed/hash-identified artifacts |
| G5 | Fresh install/upgrade, secure credential persistence, permission and lifecycle checks on named Android targets |
| G6 | Independent mobile-data/external-network tests of the owner's real hostname through each enabled provider, both HTTP and HTTPS, expected site content and management-route denial |
| G7 | Screen-off, Doze, Wi-Fi/mobile transitions, tunnel/relay reconnect, gateway outage and provider-failure isolation |
| G8 | No token leakage, verified domain ownership, valid TLS, HTTP redirect default, visitor-only route access, rate/body/queue/time limits and abuse handling |
| G9 | User runbook, DNS/TLS instructions, Relay VPS deployment, provider matrix, known limits, logs, APK/gateway hashes and owner sign-off |

No v0.1.0 release claim is allowed until the required gates pass with reproducible evidence.

## 7. Required owner-provided infrastructure

- A registered domain with permission to edit its DNS records.
- A Cloudflare account/zone and named tunnel token if Cloudflare ingress is selected.
- A public VPS/server with stable address and ports 80/443 for AssayFlux Relay. A home/mobile phone alone cannot host the public Relay gateway reliably behind carrier NAT.
- The website source or built static output. For React/Vite, import the production `dist/` contents; v0.1.0 does not compile the raw project on the phone.
