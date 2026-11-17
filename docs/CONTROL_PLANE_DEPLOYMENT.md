# Pocket Host 0.1.0 — Fleet Control Plane Deployment

## Role

The optional control plane coordinates owner identity, host enrollment, desired state, leased commands, artifact delivery, team roles and audit. It is not a website origin, runtime supervisor or required dependency for local hosting.

Android and iOS mobile applications are the primary command-center clients. Each host independently stores authorized configuration and supervises its workloads.

## Deployment topology

- Rust/Axum API and PostgreSQL run on a dedicated management network.
- Caddy or Cloudflare Tunnel provides access to the management hostname.
- AssayFlux Relay visitor gateway uses a separate visitor hostname/listener.
- Host agents initiate outbound authenticated connections.
- Website visitors reach host website ingress through Local Network, AssayFlux Relay or Cloudflare Tunnel; they never reach management endpoints.

## Host requirements

The current Compose deployment uses a Linux server with Docker Engine and Docker Compose v2, persistent PostgreSQL/artifact volumes and outbound access to required registries. This is an optional infrastructure requirement for remote fleet coordination only.

## Configuration

1. Review `control-plane/compose.yaml`, `Caddyfile` and `.env.example`.
2. Configure a dedicated management hostname and exact allowed client origins.
3. Generate independent high-entropy Owner and database credentials.
4. Configure exact trusted proxy peer IPs; do not trust an entire private subnet.
5. Choose the Caddy public-TLS or Cloudflare Tunnel profile.
6. Keep PostgreSQL and the application listener private behind the edge proxy.
7. Store all credentials in protected deployment secrets, never in source control.

## Deployment

```sh
docker compose --env-file .env -f compose.yaml up -d --build
```

The deployment workflow does not create DNS records, register hosts, configure mobile clients or perform external acceptance.

## Enrollment and operations

- Owner authenticates in the mobile command center.
- The command center requests a short-lived enrollment challenge.
- The target host proves possession of its device-bound key and displays its identity/capabilities for owner approval.
- The control plane issues a scoped, revocable host credential.
- Host agents maintain their own heartbeat and command lease.
- Commands are typed, allow-listed, scoped, expiring and idempotent.
- Hosts persist the result before acknowledgement.
- Revocation closes sessions and invalidates credentials.

## Availability contract

A control-plane/database outage may block new enrollment and remote changes. It must not stop running workloads, local host supervision or local management. Hosts retain last-authorized desired state and reconcile when connectivity returns.

## Security checks

- Management hostname is distinct from all visitor hostnames.
- Visitor listener has no administrative or device-management routes.
- No public database or raw application port.
- Exact trusted proxy peers only.
- Short-lived sessions, role checks, token rotation and audit.
- No shell, arbitrary SQL or unrestricted filesystem API.
- Device and owner credentials are separate.
- Verify revocation and command expiry from an independent client.

## Recovery

Back up PostgreSQL and artifact volumes consistently. Protect the Owner recovery material and signing keys separately. Restore the database and artifacts from the same backup point, verify host enrollment state and rotate credentials if compromise is suspected.
