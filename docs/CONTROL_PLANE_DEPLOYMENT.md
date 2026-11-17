# Pocket Host 0.1.0 — Fleet Control Plane Deployment

## Role

The optional control plane coordinates owner identity, device registry, enrollment challenges, role grants, desired state, leased jobs, artifact metadata and audit. It is not the website origin, runtime supervisor or required dependency for already-authorized local jobs.

The Android app is the current owner client foundation. An iOS command-center app is planned but no iOS source project exists in this repository. Every node stores its own device identity and accepted role/job state.

## Topology

- Rust/Axum API and PostgreSQL on a dedicated management network.
- Caddy or Cloudflare Tunnel for the management hostname.
- AssayFlux Relay visitor gateway on a separate visitor hostname/listener.
- Device agents initiate authenticated outbound connections.
- Visitor routes terminate only at approved website ingress.
- Worker data-plane routes are explicit and never expose management, runtime broker or MCU controls.

## Deployment requirements

The current Compose deployment uses Linux, Docker Engine and Docker Compose v2 with persistent PostgreSQL/artifact volumes. This is optional infrastructure for remote fleet coordination; local LAN pairing and accepted local jobs must not depend on it.

## Device enrollment and role grants

1. Owner authenticates in the mobile command center.
2. Candidate node generates a device key and short-lived one-time challenge.
3. Mobile verifies the candidate key/fingerprint and owner reviews requested roles and capabilities.
4. Owner approves exact role permissions and resource/data scope.
5. Registry issues a scoped credential bound to device key and role grant.
6. Device persists accepted state and reports health/observed assignment.
7. Owner may pause, change or revoke the role grant.

Discovery is not trust. The control plane does not accept a code alone as proof of device identity. Revocation invalidates sessions and outstanding unstarted jobs.

## Jobs and desired state

- Commands are typed, allow-listed, scoped, expiring and idempotent.
- Jobs include target device, role grant, artifact digest, input/output scopes and resource budget.
- Host agent validates locally before accepting.
- Device persists result before acknowledgement.
- Control plane stores desired state generation and observed state.
- During control-plane outage, device continues its last authorized jobs within local lease/policy.

## Configuration and deployment

1. Review `control-plane/compose.yaml`, `Caddyfile` and `.env.example`.
2. Configure a dedicated management hostname and exact allowed client origins.
3. Generate independent Owner and database credentials.
4. Configure exact trusted proxy peers; do not trust an entire private subnet.
5. Keep PostgreSQL and application listener private.
6. Store credentials in protected deployment secrets, never source control.

```sh
docker compose --env-file .env -f compose.yaml up -d --build
```

Deployment does not create DNS records, enroll devices, grant roles or prove external acceptance.

## Security and recovery

- Management and visitor hostnames are distinct.
- No public database or raw application port.
- No arbitrary shell, SQL or unrestricted filesystem API.
- Owner, mobile-client, device and role credentials are distinct.
- Test expiry, replay, role escalation, revocation and command idempotency.
- Back up database and artifact metadata consistently.
- Restore does not clone device private keys; restored devices re-enroll and receive fresh credentials.
