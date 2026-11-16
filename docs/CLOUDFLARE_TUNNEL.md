# CLOUDFLARE TUNNEL — EXISTING CONNECTOR GUIDE

**Status:** Android cloudflared integration exists in source; external hostname verification is not recorded as complete.  
**Owner context:** the domain and named Cloudflare Tunnel are already configured. Reuse them; never create a duplicate.

## 1. Purpose

Cloudflare Tunnel is one optional public-connectivity path. The Android phone remains the website origin and Rust remains the website listener. The tunnel creates outbound connectivity from the phone; it does not provide Pocket Host capacity control, visitor identity by itself or an uptime guarantee.

AssayFlux Relay is a separate first-party alternative. A bounded HTTP request/response path is now implemented in source, but it is not deployed or production-approved. WebSocket/streaming, TLS lifecycle, DNSSEC and external acceptance remain open. Local Network mode remains independent of both.

## 2. Current connector integration

- Upstream: cloudflare/cloudflared.
- Pinned source tag: `2026.11.0`.
- Build helper: `scripts/build-cloudflared-android.sh`.
- Declared ABIs: arm64-v8a, armeabi-v7a, x86_64.
- NDK minimum API: 26.
- Runtime uses `--no-autoupdate`; connector metrics bind to loopback.
- Tunnel token is passed through the intended environment channel, not command-line arguments.
- Token is encrypted at rest with AES-256-GCM and an Android Keystore key.

The Android binary is built from upstream source for this project; do not describe it as an official Cloudflare Android release.

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

## 5. Networking choice in Pocket Host

- **Cloudflare Tunnel:** use existing named tunnel and account.
- **AssayFlux Relay:** planned first-party option for users who do not want Cloudflare. Phone will establish outbound authenticated tunnel to AssayFlux gateway. Visitors see relay IP, not phone IP; relay operator can observe source IP and, in standard TLS-termination mode, HTTP content.
- **Local Network:** LAN-only hosting without public edge.
- **Direct hosting:** advanced owner-managed networking; not recommended as default.

The four choices must be independent. A relay outage must not stop local hosting or Cloudflare mode.

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
