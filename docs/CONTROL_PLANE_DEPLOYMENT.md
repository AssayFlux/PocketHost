# Pocket Host — Fleet Control Plane Deployment

**Release assignment:** v0.1.2 — Connected Device Fabric  
**Current source status:** Rust/Axum and PostgreSQL implementation exists; production enrollment and fleet acceptance remain open.

## Role

The control plane is the optional fleet coordination service. It handles owner sessions, devices, roles, desired state, typed commands, artifacts and audit. It is not the Android website origin and is not required for local or LAN static hosting.

**Important boundary:** the current source also places the Relay visitor gateway in the same Rust executable. This is an implementation coupling, not the intended product boundary. v0.1.0 must extract the owner-operated Relay Node into a separately installable service so that public website hosting does not require the full fleet control plane or its PostgreSQL database.

## Current source topology

- Rust/Axum service in `control-plane/src/main.rs`.
- PostgreSQL migrations in `control-plane/migrations/`.
- Control listener defaults to `127.0.0.1:8080`.
- Relay visitor listener defaults to `127.0.0.1:8081`.
- Caddy configuration publishes public ports 80/443 and uses on-demand TLS for verified Relay hostnames.
- Docker Compose defines PostgreSQL, the Rust service, a public Caddy profile and an optional Cloudflare Tunnel profile.
- The application requires `DATABASE_URL`, a long administrator token and a dedicated control API hostname at startup.
- The visitor listener has no admin/device routes, but shares the process and database with the fleet API.
- The Relay active session/pending request hub is in memory; PostgreSQL stores device and hostname route records.

The current deployment therefore assumes a publicly reachable Docker-capable host and PostgreSQL. It is not a no-VPS Termux Relay package.

## Intended v0.1.0 Relay boundary

The first-party Relay Node must be a separate, owner-operated service:

- Installable on Termux/Android and Linux targets declared by the device matrix.
- No paid VPS, Docker or PostgreSQL requirement.
- Independent host-ingress identity and revocation, not fleet admin or device-control credentials.
- Durable hostname/site/host route records and bounded visitor request handling.
- Public HTTP/HTTPS edge with valid TLS and HTTP redirect default.
- Visitor-only listener, never a management API.
- A reachable public IPv4/IPv6 network path remains mandatory. CGNAT without global IPv6 or port forwarding cannot accept inbound visitors.

The detailed contract is in [AssayFlux Relay](ASSAYFLUX_RELAY.md) and [Public Ingress v0.1.0](PUBLIC_INGRESS_V0_1_0.md).

## v0.1.2 fleet control-plane responsibilities

- Owner sessions, passkey/recovery flows and role-based access.
- Device registration, enrollment, approval, token rotation and revocation.
- Desired-state/configuration versions.
- Typed, allow-listed commands with leases, expiry, idempotency and durable acknowledgement.
- Artifact upload/delivery with checksums and bounded size.
- Audit events and database backup/restore.
- Linux/Android agent lifecycle and cross-device recovery.
- Fleet health and multi-host Relay scheduling/advanced failover only after the standalone v0.1.0 Relay Node is stable.

## Deployment requirements for the current Compose stack

The existing Compose stack remains a development/deployment foundation for the fleet control plane and current combined service. It requires Docker Compose, PostgreSQL, a stable public host if the edge is exposed, DNS, TLS and protected secrets. The manual GitHub deployment workflow requires an SSH deployment host and configured repository environment variables/secrets.

Do not claim that this stack has been deployed or is running in production unless a successful deployment and independent health check are recorded.

## Acceptance

For v0.1.2, test owner session lifecycle, device enrollment/revocation, role-grant signatures, typed job lease/idempotency, database recovery, audit, credential rotation, rate limits and mobile/agent reconnect. Verify visitor ingress cannot reach the management API. The v0.1.0 standalone Relay Node has its own G0–G9 acceptance and must not be treated as completed by control-plane CI alone.
