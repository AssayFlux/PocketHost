# AssayFlux Relay — Pocket Host 0.1.0 Provider Contract

## Purpose

AssayFlux Relay is Pocket Host's first-party public visitor-ingress provider. Eligible host nodes establish authenticated outbound connections to the Relay gateway. Visitors connect to verified hostnames; the gateway forwards authorized website traffic to the selected host's website ingress.

The mobile command center configures and monitors Relay. The mobile application does not carry visitor traffic and is not required for an active host agent to maintain its connection.

## Provider independence

- Local Network, AssayFlux Relay and Cloudflare Tunnel are separate connectivity providers.
- Relay and Cloudflare may be enabled simultaneously.
- Each provider has independent credentials, hostname routes, connection state, health, limits and diagnostics.
- A provider failure must not stop local hosting, another provider or unrelated services.
- Route changes and failover are explicit and auditable.

## Architecture

```
Visitor -> Relay TLS gateway -> verified hostname/site route
        -> authenticated host session
        -> host Relay agent -> Rust website ingress
        -> site router -> static or isolated component
```

Services:
1. Public TLS gateway and hostname router.
2. Owner enrollment, device identity and revocation.
3. Host Relay agent with reconnect and network-change handling.
4. Bounded stream/request transport with flow control and cancellation.
5. Domain ownership registry and certificate manager.
6. Visitor access policy enforcement.
7. Abuse controls, quotas, metrics, audit and incident operations.

## Security requirements

- Standard reviewed TLS; no custom cryptography.
- Device-bound identity and short-lived, revocable credentials.
- DNS TXT or equivalent domain ownership proof.
- Hostname bound to owner, host identity and site/component ID.
- Public visitor listener has no management, runtime broker, debug or MCU-control routes.
- Strict request/header/body/queue/connection limits.
- Backpressure, deadlines, cancellation, replay resistance and bounded reconnect.
- Visitor credentials are distinct from owner and device-management credentials.
- Relay forwards only to the host's website ingress, never directly to arbitrary local ports.

## Privacy

In standard TLS termination mode, the gateway can inspect HTTP content and metadata; disclose this to users. Hiding a host's public IP from ordinary visitors does not hide it from the Relay operator, carrier or network provider. Any end-to-end TLS pass-through mode requires a separate design and acceptance.

## Host agent lifecycle

The host agent owns its connection, process lifecycle, health and reconnect state. It runs independently of the mobile app and optional control plane after enrollment. Credential revocation closes sessions and rejects subsequent traffic. Host reboot recovery uses the locally stored authorized configuration and device-bound credential.

## Current source contract

The repository contains a bounded HTTP request/response visitor path between the control-plane gateway and Android Relay agent. It is not a general TCP, WebSocket or unrestricted streaming tunnel. The documented source limits include 8 MiB request/response bodies, a 45-second request deadline and four concurrent local requests per device. These source limits are not a production service guarantee.

Public operation requires deployed gateway, verified DNS/TLS, real external HTTPS tests, reconnect/soak/load testing, abuse controls and security review. A connected agent alone does not prove visitor reachability.

## Acceptance

- Enrollment, ownership proof, host binding and immediate revocation.
- Host reboot, phone outage and control-plane outage do not terminate eligible host-local service lifecycle.
- Host network change, reconnect and session replacement cancel stale requests safely.
- Concurrent bounded requests, backpressure, cancellation and timeouts.
- No hostname route to management or device administration.
- Cloudflare and Local Network continue when Relay is unavailable.
- External DNS/TLS/visitor policy tested from an independent network.
- Load, abuse, privacy and operational readiness reviewed.
