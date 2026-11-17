# Pocket Host — Relay Gateway & Fleet Control Plane

The Rust service has separate management and visitor-ingress listeners. The minimal AssayFlux Relay gateway is a v0.1.0 dependency for owners who choose first-party public hosting; it can be deployed on an owner-controlled public VPS/server. Full fleet coordination (owner sessions, device enrollment, role grants, desired state, leased jobs and worker orchestration) remains v0.2.0. The Android app is the current owner client foundation; an iOS app is planned and is not present in this repository.

The management API does **not** serve website traffic. The separate Relay visitor listener does serve bounded website requests. Neither service is required for local hosting or continued operation of the local website server. Every host persists local configuration and supervises its own services. A control-plane outage delays remote coordination; it must not stop active services.

## Components

- Rust/Axum API.
- PostgreSQL identity, device, desired-state, command and audit records.
- Device enrollment and revocation.
- Short-lived administrative sessions and role-based access.
- Leased, expiring, allow-listed commands with durable acknowledgement.
- Artifact upload and integrity-checked host delivery.
- Separate visitor Relay listener; visitor ingress has no administrative or device-management routes.

## Deploy

1. Review `compose.yaml`, `Caddyfile` and the deployment guide at `../docs/CONTROL_PLANE_DEPLOYMENT.md`.
2. Copy `.env.example` to `.env`.
3. Configure the dedicated management hostname and exact HTTPS origin of the mobile/web clients.
4. Generate unique high-entropy Owner and database credentials.
5. Deploy:

```sh
docker compose --env-file .env -f compose.yaml up -d --build
```

Only the configured edge proxy publishes public ports. PostgreSQL and the API remain on the private container network. Persist and back up database and artifact volumes. Never commit `.env`.

## Mobile and host enrollment

The Android Command Center contains a local trust-authority foundation and a preview control-plane client. It can bootstrap/register a worker and queue an owner-signed grant and typed health-check command, but full end-to-end enrollment remains incomplete: device-token delivery is manual, transport is not mutually authenticated, status/acknowledgement UX and revocation propagation are unfinished, and no iOS client is present. The intended production contract is for each host to store its own device-bound credential securely and maintain its own connection, heartbeat, command lease and workload lifecycle.

Host commands are typed, allow-listed, scoped, expiring and idempotent. Hosts validate owner policy and local capability before applying them. The host persists operation start/result before acknowledgement to prevent blind repetition after interruption.

## Fault-containment contract

- Mobile app termination does not stop host workloads.
- Control-plane or database outage does not stop active host workloads.
- Visitor Relay failure does not expose or disable management endpoints.
- Device revocation closes active control sessions and rejects new requests.
- Host agent failure is local to that host; unrelated hosts continue.
- Website visitor ingress and management endpoints use separate listeners/hostnames.

## AssayFlux Relay — v0.1.0

The Relay visitor listener accepts only DNS-verified hostnames and forwards bounded visitor requests to the authenticated host agent. It is separate from the management API and uses its own public TLS edge. The current transport is bounded HTTP request/response, not a general-purpose TCP/WebSocket tunnel. The owner must deploy the gateway on a public VPS/server, configure DNS and valid TLS, then verify external reachability, reconnect, limits and security before advertising public availability.

Cloudflare Tunnel is an independent v0.1.0 provider and may be used at the same time on a different hostname. Both providers target website ingress only. Full fleet management remains v0.2.0.

## Security

- Store API credential hashes, not raw credentials.
- Use short-lived sessions, role checks, device-bound credentials and revocation.
- Do not expose PostgreSQL or the raw application listener publicly.
- Trust forwarded client/protocol headers only from exact configured proxy peers.
- Never expose arbitrary shell, SQL, filesystem or management operations.
- Audit sensitive changes.
- Protect signing keys, secrets, database backups and artifact volumes.
