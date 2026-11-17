# Cloudflare Tunnel — Provider Specification

**Release assignment:** v0.1.0 — First Hosting public-domain acceptance.  
**Role:** independent optional visitor-ingress adapter.

## Purpose

Cloudflare Tunnel provides an outbound connection from the Android host to an existing Cloudflare tunnel. It does not provide Pocket Host capacity management, visitor identity by itself or an uptime guarantee.

AssayFlux Relay and Local Network are independent. A provider failure must not stop another provider or local hosting.

## Existing repository integration

- Upstream cloudflared source is pinned by the build helper.
- Android native build helper: scripts/build-cloudflared-android.sh.
- Declared Android ABIs: arm64-v8a, armeabi-v7a and x86_64.
- NDK minimum API: 26.
- Runtime uses --no-autoupdate and connector metrics bind to loopback.
- Token is passed through the intended environment channel, not command-line arguments.
- Token is encrypted at rest using AES-256-GCM and Android Keystore.

This is integration source, not proof of public reachability or production certification.

## v0.1.0 public HTTP/HTTPS contract

The owner configures a Cloudflare named tunnel and maps the chosen public hostname to the Android static server origin, normally `http://127.0.0.1:8080` (or the configured website port). Visitors use the public hostname; the phone makes an outbound tunnel connection and does not open a new Internet-facing listener. Cloudflare terminates visitor TLS and forwards to the local HTTP origin.

Both HTTP and HTTPS entry points must be reachable. The default policy redirects HTTP to HTTPS; plain HTTP content serving requires an explicit owner opt-in and warning. Cloudflare DNS and certificate issuance are configured in the owner's Cloudflare zone. Pocket Host does not silently change DNS or claim a hostname is live merely because the connector reports ready.

## Safe use of an existing named tunnel

1. Use the owner's existing named tunnel; do not create a duplicate unnecessarily.
2. Treat the tunnel token as a password.
3. Enter it only through the secure token field in Pocket Host.
4. Confirm the public hostname routes to the Rust website listener and correct port.
5. Never route the public hostname to management API, browser dev server or control-plane administration.
6. Start local hosting before enabling the connector.
7. Test the hostname from mobile data or another independent network.
8. Verify expected site content and TLS.

Never share tunnel tokens in chat, source, screenshots or logs.

## Connection states

| State | Meaning | Does not prove |
|---|---|---|
| Token configured | Encrypted credential stored | Connector running |
| Connector ready | Local process/health probe ready | Public hostname reachable |
| External HTTPS verified | Independent client reaches expected site with valid TLS | Long-term uptime |
| Visitor access secured | Access policy tested | Management API safety without separate tests |

## Security and operations

- Rotate exposed credentials.
- Never log token or put it in process arguments.
- Keystore protects stored credentials but not a rooted device or process memory.
- Public website traffic terminates at Rust; management API remains private.
- Use Cloudflare Access for private sites where appropriate.
- Test reconnect, Wi-Fi/mobile transition, screen-off, battery saver, process restart and Android lifecycle.
- Do not promise 24/7 uptime.

## Acceptance

Verify token encryption, connector start/stop, public DNS/TLS, correct website origin, unknown-host rejection, management-route isolation, access policy, token rotation and independent provider failure. A local readiness probe is not external verification. Apply v0.1.0 G0–G9 and record an external-network result.
