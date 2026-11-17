# Cloudflare Tunnel — Provider Specification 0.1.0

**Product version:** 0.1.0  
**Provider role:** independent public visitor-ingress adapter for eligible Pocket Host host nodes. Reuse them; never create a duplicate.

## 1. Purpose

Cloudflare Tunnel is one optional public-connectivity path. The selected Pocket Host host node is the website origin and its website ingress is the only allowed tunnel destination. The tunnel creates outbound connectivity from the phone; it does not provide Pocket Host capacity control, visitor identity by itself or an uptime guarantee.

AssayFlux Relay is a separate first-party provider and may operate concurrently with Cloudflare Tunnel. A bounded HTTP request/response path is now implemented in source, but it is not deployed or production-approved. WebSocket/streaming, TLS lifecycle, DNSSEC and external acceptance remain open. Local Network mode remains independent of both.

## 2. Current connector integration

- Upstream: cloudflare/cloudflared.
- Pinned source tag: `2026.11.0`.
- Build helper: `scripts/build-cloudflared-android.sh`.
- Declared ABIs: arm64-v8a, armeabi-v7a, x86_64.
- NDK minimum API: 26.
- Runtime uses `--no-autoupdate`; connector metrics bind to loopback.
- Tunnel token is passed through the intended environment channel, not command-line arguments.
- Token is encrypted at rest with AES-256-GCM and an Android Keystore key.

The current cloudflared build helper targets Android ABIs. Other host platforms require their own verified cloudflared packages and lifecycle adapters.

## 3. Connect the existing named tunnel

1. Open the existing named tunnel in Cloudflare Zero Trust.
2. Identify the token for that exact tunnel. Treat it as a password.
3. In Pocket Host tunnel settings, enter it through the secure token field. Never paste it into chat, source, screenshots or logs.
4. Inspect the existing Public Hostname route.
5. Ensure its service points to the Pocket Host Rust website listener and correct local port.
6. Never point the public hostname at the management API, console dev server or control-plane administration route.
7. Start Pocket Host hosting, then enable the connector.
8. From mobile data or another independent network, request the hostname and verify TLS plus the expected website response.
9. Preserve the existing tunnel and DNS configuration when it already works; change only the origin if required.

## 4. Distinguish connection states

| State | Meaning | Does not prove |
|---|---|---|
| Token configured | Encrypted credential is stored | Connector is running |
| Connector ready | Local cloudflared process and health probe are ready | Public hostname works |
| External HTTPS verified | Independent client reaches expected site over valid TLS | Long-term uptime |
| Visitor access secured | Cloudflare Access or site policy blocks unauthorized visitors | Management API is safe unless separately tested |

## 5. Provider independence and mobile control

- **Cloudflare Tunnel:** use existing named tunnel and account.
- **AssayFlux Relay:** independent first-party provider. Each eligible host establishes its own authenticated outbound session; provider health, hostnames and credentials are independent of Cloudflare.
- **Local Network:** LAN-only hosting without public edge.
- **Direct hosting:** advanced owner-managed networking; not recommended as default.

The mobile command center can configure and observe providers across enrolled hosts. Local Network, AssayFlux Relay and Cloudflare Tunnel are independent; Relay and Cloudflare may run concurrently. Failure in one must not stop the others.

## 6. Security and operations

- Tunnel token is a device secret; rotate in Cloudflare if exposed.
- Do not log tokens or place them in process arguments.
- Keystore encryption protects stored credentials but not a rooted/compromised device or secrets in process memory.
- Public website traffic must terminate at Rust. Management API stays loopback-only and authenticated.
- Use Cloudflare Access for private websites where required; tunnel connectivity alone is not visitor authentication.
- Monitor connector restart count, reconnect delay, network transitions and device battery/thermal state.
- Explain Android background restrictions; do not promise 24/7 availability.

## 7. Acceptance checklist

- [ ] Existing named tunnel reused; no duplicate created.
- [ ] Token entered through secure UI and encrypted at rest.
- [ ] Token absent from logs, process arguments, backups and diagnostics.
- [ ] Connector reaches local ready state.
- [ ] Public Hostname targets Rust listener only.
- [ ] External HTTPS returns intended site from another network.
- [ ] Unknown host rejected.
- [ ] Management API cannot be reached through public hostname.
- [ ] Access policy verified for private sites.
- [ ] Wi-Fi/mobile transition, screen-off, battery saver and process restart tested on physical device.
- [ ] Token rotation invalidates old connector session.

A successful local readiness probe is not external verification.
