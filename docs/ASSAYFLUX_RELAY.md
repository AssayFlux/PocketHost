# AssayFlux Relay — Public Visitor Ingress Contract

**Release assignment:** v0.1.0 — First Hosting public-domain acceptance.  
**Current source:** bounded HTTP request/response visitor path; public production operation not certified.

## Purpose

AssayFlux Relay is a first-party public visitor-ingress provider. An eligible host establishes an authenticated outbound connection to a Relay gateway. Visitors connect to verified hostnames; the gateway forwards authorized website traffic to the host's Rust website ingress.

## Provider independence

Local Network, AssayFlux Relay and Cloudflare Tunnel are independent providers. Relay and Cloudflare may operate simultaneously. Each has separate credentials, hostname routes, health, limits and diagnostics. Failure in one must not stop local hosting or another provider.

## Architecture

Visitor → Relay TLS gateway → verified hostname/site route → authenticated host session → host Relay agent → Rust website ingress.

The provider must include domain ownership proof, host binding, TLS, bounded request transport, backpressure, cancellation, quotas, audit and abuse controls.

## Security and privacy

- Device-bound identity and short-lived revocable credentials.
- DNS TXT or equivalent domain ownership proof.
- Hostname bound to owner, host identity and site ID.
- Visitor listener has no management, runtime broker, debug or MCU routes.
- Strict request/header/body/queue/connection limits.
- Standard TLS only; no custom cryptography.
- Visitor credentials are distinct from owner/device-management credentials.
- Relay forwards only to website ingress, never arbitrary local ports.

In standard TLS termination mode, the gateway can inspect HTTP content and metadata. Disclose this. Hiding the host's public IP from visitors does not hide it from the Relay operator or network provider.

## v0.1.0 deployment boundary

The owner deploys the Relay gateway on a public VPS/server with stable DNS and public TCP ports 80/443. The repository's current implementation places the Relay visitor listener on its own bind address/port, separate from the control API listener, and uses the deployment reverse proxy for public TLS. The Relay gateway needs a minimal host/site registration and revocation path; the complete fleet control plane and multi-device administration UX are not prerequisites for static website serving.

The Android host maintains an outbound authenticated HTTPS/SSE connection and receives bounded visitor requests. The phone must not accept unsolicited Internet connections. The gateway validates hostname ownership (DNS TXT challenge), binds the hostname to an owner/device/site route and forwards only to that registered website. Do not route arbitrary local ports.

Public HTTPS requires a valid certificate at the Relay edge. HTTP redirects to HTTPS by default; an owner may explicitly opt into plain HTTP content serving. The visitor listener is never the admin API. Restrict the admin hostname to an owner-only access policy or private network and verify the visitor hostname cannot reach admin, runtime, debug or MCU paths.

## Current source boundary

The repository contains a bounded HTTP request/response path. Documented source limits include 8 MiB request/response bodies, a 45-second request deadline and four concurrent local requests per device. This is not a general TCP, WebSocket or unrestricted streaming tunnel, and is not a production service guarantee.

## Acceptance

Prove domain ownership, host binding, revocation, external DNS/TLS, request limits, cancellation, timeout, backpressure, reconnect, abuse controls and management-route isolation from an independent network. Test Relay failure while Local Network and Cloudflare remain available. Apply v0.1.0 G0–G9 and record an external-network result.
