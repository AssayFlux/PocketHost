# Pocket Host — Fleet Control Plane Source

**Release placement:** v0.2.0 fleet control plane.  
**Current source:** Rust/Axum fleet API and a partially integrated Relay visitor gateway share this executable. This repository directory is not yet the standalone Termux Relay Node product.

## What is present

- Rust/Axum management API.
- PostgreSQL migrations for owner/device state, enrollment, sessions, commands, audit and hostname/site routing.
- Separate management and visitor HTTP listeners.
- Relay DNS TXT hostname proof and visitor route handling.
- In-memory Relay session/pending-request hub.
- Caddy reverse proxy with on-demand TLS for verified Relay hostnames.
- Docker Compose with PostgreSQL, the Rust service, public Caddy profile and optional Cloudflare Tunnel profile.
- Manual SSH-based deployment workflow.

The visitor listener is separate from the management listener, but the current implementation still shares one process, database and fleet device credential model. The in-memory RelayHub loses active sessions and pending requests on process restart.

## Current deployment requirements

The existing Compose deployment requires:

- Docker Compose.
- PostgreSQL.
- A publicly reachable host/network if visitor traffic is to enter directly.
- DNS records and TLS edge configuration.
- `.env` values including `DATABASE_URL`, the control-plane admin token and control hostname.

Example development deployment:

```sh
cp .env.example .env
# Edit .env with unique development credentials.
docker compose --env-file .env -f compose.yaml up -d --build
```

Never commit `.env`, database credentials, admin tokens or signing keys.

The GitHub deployment workflow is manual and requires an SSH host, user, path, domain and configured environment secrets. A deployment workflow definition is not evidence that a production instance exists.

## AssayFlux Relay — v0.1.0 target

The first-party Relay must be extracted into a separately installable owner-operated Relay Node:

- Termux/Android and Linux targets.
- No paid VPS, Docker or PostgreSQL prerequisite.
- Independent Relay host identity and revocable ingress credentials.
- Durable hostname/site/host binding.
- Public TLS and HTTP-to-HTTPS redirect by default.
- Visitor-only forwarding to the registered website origin.
- Minimal local owner setup, status, start/stop and recovery interface.

A public network endpoint is still required: public IPv4 with router port forwarding or globally routed IPv6. A device behind CGNAT without global IPv6 cannot accept inbound visitors. The main website-host phone may remain behind CGNAT because its connection to the Relay Node is outbound.

## Current Android coupling to remove

The Android `RelayAgent` now obtains its endpoint and host token from the dedicated `RelayCredentialStore`; `HostingService` starts Relay independently of remote-control configuration. The node-side credential issuer and standalone gateway remain unimplemented. v0.1.0 must give Relay its own configuration, host identity, credential rotation/revocation and lifecycle. Public website hosting must not require fleet enrollment or admin credentials.

## v0.2.0 fleet control-plane scope

- Owner sessions/passkeys and role-based access.
- Device registration, enrollment, approval, credential rotation and revocation.
- Desired state and versioned configuration.
- Typed allow-listed commands with leases, expiry, idempotency and durable acknowledgement.
- Artifact delivery, checksums, audit, health and database recovery.
- Linux/Android agent lifecycle and cross-device recovery.
- Multi-host Relay scheduling, regional scale and advanced failover after the standalone Relay Node is accepted.

## Fault-containment requirements

- Mobile app termination must not stop accepted local website hosting.
- Control-plane/database outage must not stop local hosting or a standalone Relay Node's already-accepted local routing.
- Visitor Relay failure must not expose or disable management endpoints.
- Device revocation must close active control sessions and reject new privileged requests.
- Website visitor ingress and management endpoints must remain separate listeners/hostnames.
- No arbitrary shell, SQL, filesystem or management operations are exposed to website visitors.

For current source facts and release boundaries, see [Project Baseline](../docs/PROJECT_BASELINE.md), [Repository Deep Audit](../docs/REPOSITORY_AUDIT.md), [AssayFlux Relay](../docs/ASSAYFLUX_RELAY.md) and [Control Plane Deployment](../docs/CONTROL_PLANE_DEPLOYMENT.md).
