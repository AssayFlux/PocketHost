# Security Gate 4 — Owner and visitor identity

**Status: partially implemented; gate remains open.** This document distinguishes implemented source from verified runtime behavior.

## Implemented in source

### Remote administration
- Bootstrap/team API credentials are exchanged only at `POST /v1/admin/session`.
- Management sessions are 12-hour random bearer values stored as hashes.
- Sessions can be explicitly revoked and are invalidated on user revocation or team-user token rotation.
- Sensitive team-user/device lifecycle changes and audit records commit transactionally.
- The web console keeps the long-lived credential out of persistent browser storage and holds the session in memory.
- PostgreSQL CI covers viewer authorization, session expiry/revocation, account revocation and token-rotation invalidation.

### Site visitor access
- Each local site has a separate `accessMode`: `public`, `private` or `restricted`.
- Private mode has one shared `visitor` identity; Restricted mode has named invitations. An empty credential set fails closed.
- The phone generates 256-bit visitor secrets once, stores only lowercase SHA-256 hashes, and supports Owner invite/rotation/revoke actions.
- Site policy and credential hashes are passed into the Rust HTTP route table. Rust validates the Basic Authorization value with a constant-time hash comparison.
- Protected routes require HTTPS indicated by a trusted loopback proxy header; a direct non-TLS LAN request is rejected.
- Visitor Authorization is removed before proxying to Python/Node so customer code does not receive the PocketHost visitor credential.
- The app's encrypted full backup exports hostname routes but deliberately excludes visitor credential hashes; restore defaults to Public and requires explicit reconfiguration.

## APIs

Local loopback management API (Owner session required):
- `PUT /api/v1/sites/{siteId}/access` with `{"mode":"public|private|restricted"}`
- `POST /api/v1/sites/{siteId}/visitors` with `{"username":"alice"}` for Restricted, or no username for Private
- `DELETE /api/v1/sites/{siteId}/visitors/{username}`

The create response returns the visitor password once. Browser navigation uses HTTP Basic authentication: username is `visitor` for Private, or the invitation name for Restricted. The visitor password is the one-time generated secret.

## Remaining work before Gate 4 closes

1. Implement passkey-first Owner identity using a platform credential / WebAuthn-compatible flow with secure recovery; the existing password + device-approved recovery flow is not a passkey.
2. Add visitor authentication throttling and abuse controls per site/IP, with bounded audit events and lockout behavior that does not allow trivial denial of service.
3. Add explicit fresh Owner reauthentication for sensitive local management actions and test role separation across every mutation route.
4. Verify TLS-proxy trust configuration cannot be forged by a non-loopback peer and test direct LAN rejection.
5. Execute Android instrumentation tests for site mode changes, visitor creation/rotation/revocation, browser 401 challenge, cross-site credential isolation, runtime proxy stripping and backup secret exclusion.
6. Run adversarial cross-site token reuse and revoked-token tests against the real native server and physical Android device.

## Acceptance tests

- Public site serves without visitor credentials.
- Private site returns 401 with a Basic challenge unless the one shared credential is valid.
- Restricted site accepts only the named invite and its token; another site's token must not work.
- Revoking or rotating a visitor credential invalidates it on the next request.
- Empty Private/Restricted credential registry denies all visitors.
- Protected sites reject direct HTTP and accept only HTTPS at the trusted loopback proxy.
- Customer runtime receives no PocketHost visitor Authorization header.
- Viewer/Operator cannot mutate site access or visitor invitations.
- Backup/restore never exports visitor hashes or raw tokens.

No production-ready claim is permitted until the remaining work and executed acceptance evidence are recorded.
