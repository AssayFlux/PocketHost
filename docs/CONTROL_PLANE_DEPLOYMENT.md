# Pocket Host — Fleet Control Plane Deployment

**Release assignment:** minimal Relay gateway deployment in v0.1.0; full fleet control-plane acceptance in v0.2.0.  
**Local hosting dependency:** none.

## Role

The optional Rust/Axum service contains two deliberately separate planes: a visitor-only Relay gateway (single-host public-ingress requirement in v0.1.0) and the owner/device fleet control API (full acceptance in v0.2.0). It is not the website origin, runtime supervisor or required dependency for local static hosting. The v0.1.0 Relay deployment must not make the full admin API public to visitors.

## Topology

- Android Pocket Host remains the owner mobile interface and website-serving node.
- Separately installed agents connect over authenticated transport.
- The control plane stores coordination state and dispatches typed commands/jobs.
- Website visitor ingress is a separate listener/hostname from management/device-control endpoints. The visitor listener is v0.1.0 scope; fleet enrollment/jobs remain v0.2.0.
- Browser operator console is a separate web-console build.

## Deployment requirements

- TLS at every external boundary.
- Protected owner sessions and short-lived device credentials.
- PostgreSQL migrations with backup and restore procedure.
- Secret management outside source control.
- Rate limits, request/body limits, audit retention and monitoring.
- Private management routes; no visitor hostname may proxy to admin APIs.
- Explicit health checks, upgrade and rollback procedure.
- Control-plane outage must not invalidate already-approved local work before its lease/policy expires.

## Enrollment and jobs

The control plane may transport signed owner decisions but does not replace owner approval. Device agents verify signed grants and typed job envelopes locally. Unknown operations, roles, capabilities, expired grants and wrong target IDs are rejected.

## Current source status

The repository includes a Rust/Axum API foundation, PostgreSQL migrations, session/passkey foundation, device commands, audit and Compose/Caddy deployment configuration. Source presence does not prove production deployment, security hardening or end-to-end Android integration.

## Acceptance

Deploy to a controlled environment, test TLS/session lifecycle, owner enrollment, grant issuance/revocation, job lease/idempotency, database recovery, audit, rate limits, backup/restore and mobile/agent reconnect. Public visitor ingress must not reach the control plane. For the minimal Relay gateway profile, apply v0.1.0 G0–G9. Apply v0.2.0 G0–G9 to full fleet control-plane acceptance.
