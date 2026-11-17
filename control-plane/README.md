# Pocket Host 0.1.0 — Fleet Control Plane

The control plane is an optional, separately deployed fleet coordination service. Android and iOS mobile applications are the primary owner command centers. Host agents connect to the control plane for enrollment, heartbeats, desired state, leased commands, artifact delivery, team roles and audit.

The control plane does **not** serve website traffic and is not required for local hosting or continued operation of already-authorized workloads. Every host persists local configuration and supervises its own services. A control-plane outage delays remote coordination; it must not stop active services.

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

The Android/iOS command center creates an owner-authorized enrollment flow. The host receives a device-bound credential that is shown only once and stored using the platform's secure credential store. The host agent—not the mobile app—maintains its own connection, heartbeat, command lease and workload lifecycle.

Host commands are typed, allow-listed, scoped, expiring and idempotent. Hosts validate owner policy and local capability before applying them. The host persists operation start/result before acknowledgement to prevent blind repetition after interruption.

## Fault-containment contract

- Mobile app termination does not stop host workloads.
- Control-plane or database outage does not stop active host workloads.
- Visitor Relay failure does not expose or disable management endpoints.
- Device revocation closes active control sessions and rejects new requests.
- Host agent failure is local to that host; unrelated hosts continue.
- Website visitor ingress and management endpoints use separate listeners/hostnames.

## AssayFlux Relay

The Relay visitor listener accepts only verified hostnames and forwards bounded visitor requests to an enrolled host agent. It is separate from the management API. The current transport is bounded HTTP request/response, not a general-purpose TCP/WebSocket tunnel. Verify deployment, DNS/TLS, external reachability, reconnect, limits and security before advertising public availability.

Cloudflare Tunnel is an independent provider and may be used concurrently. Both providers target website ingress only.

## Security

- Store API credential hashes, not raw credentials.
- Use short-lived sessions, role checks, device-bound credentials and revocation.
- Do not expose PostgreSQL or the raw application listener publicly.
- Trust forwarded client/protocol headers only from exact configured proxy peers.
- Never expose arbitrary shell, SQL, filesystem or management operations.
- Audit sensitive changes.
- Protect signing keys, secrets, database backups and artifact volumes.
