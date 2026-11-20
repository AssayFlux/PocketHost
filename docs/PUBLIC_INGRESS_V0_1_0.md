# Pocket Host — Public Ingress v0.1.0

**Release:** v0.1.0 — First Hosting  
**Status:** native Android Relay and Cloudflare Tunnel integrations are implemented in source; real public-network acceptance remains incomplete.  
**Native Relay design:** [NATIVE_ANDROID_RELAY.md](NATIVE_ANDROID_RELAY.md)

## 1. User outcome and providers

An owner imports a static website into the Pocket Host Android app and makes it reachable from ordinary Internet-connected browsers using the owner's domain. v0.1.0 has two independent public-ingress providers:

1. **Cloudflare Tunnel:** optional third-party tunnel integration using an owner-managed Cloudflare account and named tunnel.
2. **AssayFlux Relay:** first-party native Android service bundled in the Pocket Host APK. Kotlin owns the foreground-service lifecycle and Android integration; a Rust/Tokio engine built for Android ABIs handles inbound visitor HTTP(S), outbound authenticated host sessions, TLS, routing and bounded forwarding through JNI.

The native Relay is not a separate APK, terminal-managed package or external runtime. It must not require a terminal app, Node.js/npm, Docker, PostgreSQL or the full fleet control plane. Local preview and LAN hosting continue if either public provider or the Internet is unavailable.

## 2. Traffic model

A Relay Android device accepts inbound public visitor connections and maintains authenticated outbound host sessions. The website-host phone may remain behind mobile carrier NAT because its host-side RelayAgent initiates an outbound HTTPS/SSE connection.

The existing host wire contract is the compatibility baseline:

- `GET /v1/device/relay/stream` — Bearer host credential and Server-Sent Events.
- `POST /v1/device/relay/responses/{id}` — same credential and JSON response.

The native gateway must authenticate each host session, correlate requests, reject stale/duplicate responses and recover cleanly after reconnect. Public visitor requests must reach only the explicitly verified hostname/site route. Owner setup and pairing remain local-only in v0.1.0.

## 3. Public reachability and Android port constraints

“No VPS required” does **not** mean “no publicly reachable endpoint required.”

An ordinary unrooted Android app cannot bind TCP ports below 1024. The native listener therefore uses configurable unprivileged ports with defaults HTTP 8082 and HTTPS 8444. This avoids the existing website-engine default of 8080 and embedded Python default of 8081. For conventional public URLs, the owner's router, firewall or reverse proxy must forward public TCP 80 to 8082 and public TCP 443 to 8444. Relay must validate that configured ports do not overlap active local-host listeners. IPv6 requires equivalent edge forwarding or proxying; a globally routed IPv6 address alone does not grant an Android app permission to bind port 443.

The Android Relay device needs one of:
- public IPv4 with router/edge port forwarding;
- globally routed IPv6 with firewall and port-forwarding/proxy rules for the app's unprivileged ports; or
- another owner-controlled public edge that forwards to the Android listener.

If the only network is behind CGNAT, has no globally routed IPv6 and offers no inbound forwarding, that device cannot accept public visitors. DNS does not bypass CGNAT. Cloudflare Tunnel remains an optional fallback. The website-host phone may remain behind CGNAT because its connection to the Relay is outbound.

The app must display the configured listener ports and separately report local listener readiness, host-session connectivity, DNS proof, certificate readiness and externally verified public access.

## 4. HTTP, HTTPS and hostname ownership

- Require DNS TXT proof before activating a hostname.
- Bind each verified hostname to a selected site and authenticated host session.
- Serve valid HTTPS and redirect HTTP to HTTPS by default.
- Document and test the ACME/certificate issuance and renewal method against the required public edge forwarding.
- Fail closed for unknown, expired or revoked hostnames.
- Do not trust arbitrary client-IP forwarding headers. Trust only explicitly configured edge proxies; otherwise use the socket peer address.
- Plain HTTP content serving is not part of the initial secure default and must not be advertised unless implemented as an explicit warned opt-in.

A local listener or “connected” status is not proof of public DNS, TLS or visitor content. Verify from mobile data or another independent network.

## 5. Security boundaries

- Public visitor routes are website data-plane routes only.
- Never expose local management APIs, pairing, owner settings, device commands, runtime brokers, Workbench drafts, fleet APIs, debug endpoints or MCU controls through a visitor hostname.
- Host-session endpoints use separate high-entropy, scoped and revocable credentials.
- Never reuse fleet admin, RemoteControlAgent, local management or Cloudflare credentials.
- Protect node identity with Android Keystore; store credential verifiers on the Relay node and protect host-side secrets with Android Keystore.
- Persist hostname proofs, route bindings, credential rotation/revocation and certificate metadata in a local durable store. Active sockets are re-established after restart; in-flight requests fail boundedly.
- Enforce body/response limits, concurrency, pending-queue limits, per-host quotas and timeouts.
- v0.1.0 provides bounded HTTP forwarding, not arbitrary TCP tunnelling, a generic proxy/VPN or implicit WebSocket support.

## 6. Current implementation and remaining acceptance

Current Alpha source contains:
- Kotlin `NativeRelayService` and JNI bridge to the Rust/Tokio `relay-node` engine, bundled in the main APK.
- Android Keystore-backed node identity and host credential protection; encrypted transactional SQLite configuration, pairing-invite hashes, host credential verifiers and revocation records.
- Short-lived single-use host pairing invitations with proof-of-possession and owner approval; owner credential issuance/rotation/revocation.
- Bounded inbound visitor HTTP(S), authenticated outbound HTTPS/SSE host sessions, correlated request/response forwarding and visitor-path/control-surface rejection.
- DNS TXT ownership verification before route activation, Rustls HTTPS, HTTP-to-HTTPS redirects, ACME HTTP-01 issuance and scheduled certificate-expiry/renewal checks.
- Gradle/GitHub Actions native Relay builds for the declared Android ABIs.
- An independent Cloudflare Tunnel option. Caddy trusts only the configured Cloudflared peer for visitor-IP metadata; a Docker spoofing test passes in quality run #44687694785.

Remaining v0.1.0 acceptance:
- Recheck DNS ownership continuously and prove revocation/route removal behavior with real DNS.
- Exercise ACME issuance and renewal against an owner-controlled public domain and supported edge forwarding.
- Verify visitor/owner/management route isolation, host revocation and reconnect using integrated live traffic.
- Verify Android foreground service, boot recovery, battery/Doze and network changes on named physical devices.
- Verify Android process/device restart with live host and visitor traffic; live sessions/pending requests are ephemeral and must fail closed.
- Build and inspect the final release APK, all declared ABI libraries, signature, SHA-256 and provenance.
- Verify DNS, TLS, HTTP redirect and real visitor content from independent networks. No physical-device or independent public-domain acceptance is yet recorded.

The native Android Relay is the v0.1.0 product gateway and does not require the Compose/Caddy fleet control plane, PostgreSQL, Docker or a terminal-managed environment. The Cloudflare Tunnel is an independent optional provider. A source implementation, local listener or “connected” status is not proof of public visitor reachability.

## 7. Release gates

| Gate | Required public-ingress evidence |
|---|---|
| G0 | Freeze provider behavior, hostname/DNS/TLS contract, Android ABI matrix, listener ports, edge forwarding, CGNAT limitations and supported topology |
| G1 | Review node identity, host credentials, TLS, visitor/host/owner separation, Android foreground service, JNI boundary, resource limits and trusted-proxy behavior |
| G2 | Deliver native Android Relay engine/service, inbound listener, outbound host sessions, pairing, durable routes, DNS proof, TLS, revocation and recovery |
| G3 | Pass Rust, protocol, persistence, JNI, Kotlin lifecycle, route-isolation and negative security tests |
| G4 | Build the main APK with Relay native libraries for every declared ABI; inspect APK contents, signatures, provenance and SHA-256 |
| G5 | Install/upgrade the APK and migrate Relay state on named physical Android devices |
| G6 | Verify real DNS/TLS and visitor content from an independent network through the declared public edge |
| G7 | Test service/process/device restart, network loss, host reconnect, stale requests and provider outage |
| G8 | Prove visitor routes cannot reach management APIs and credentials are scoped/revocable |
| G9 | Publish Android Relay setup, network/port-forwarding guide, known limits, support matrix and reproducible evidence |

A source file, design document, successful compile or local readiness probe does not close a physical-device or public-network gate.
