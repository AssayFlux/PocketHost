# Security Gate 4 — Owner and visitor identity

**Status: partially implemented; gate remains open.** This document distinguishes implemented source from verified runtime behavior.

## Implemented in source

### Remote administration
- Bootstrap/team API credentials are exchanged only at `POST /v1/admin/session`.
- Management sessions are 12-hour random bearer values stored as hashes.
- Sessions can be explicitly revoked and are invalidated on user revocation or team-user token rotation.
- Sensitive team-user/device lifecycle changes and audit records commit transactionally.
- The web console keeps the long-lived credential out of persistent browser storage and holds the session in memory.
- Remote write operations require a session issued within the previous five minutes; stale sessions receive HTTP 428. The console retains the bootstrap/team credential only in component memory, exchanges it for a fresh session after the server challenge, and revokes the superseded session. Read-only access remains available for the 12-hour session lifetime.
- PostgreSQL CI covers viewer authorization, session expiry/revocation, account revocation, token-rotation invalidation and the five-minute remote write reauthentication window.
- Device credential rotation is Owner-only; the role predicate rejects Editor and Viewer.

### Owner passkey step-up
- Sensitive writes are rejected with HTTP 428 when the server-side session was authenticated more than five minutes ago.
- The remote console handles this response by invoking WebAuthn with user verification and exchanging the fresh assertion for a new credential-bound Owner session before retrying the write.
- Before the first passkey enrollment, the bootstrap credential remains available for initial setup and five-minute reauthentication. The first successful passkey registration enables the database passkey-required policy and revokes all bootstrap-derived sessions. Subsequent bootstrap session exchanges fail closed.
- Lost-device recovery uses ten one-time random 256-bit codes. Only SHA-256 hashes are stored. Each code is atomically consumed once and creates a 12-hour recovery session; the next passkey enrollment rotates the recovery set. Recovery is rate-limited and audited.
- This provides WebAuthn user-verification step-up, but it is not a claim that every authenticator performs biometric verification specifically: authenticators may satisfy user verification using biometrics or a device PIN/passcode. Route-by-route instrumentation and real browser/authenticator acceptance remain open.

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

## Passkey implementation now present in source

- `POST /v1/admin/passkeys/registration/start` and `/finish` implement a server-challenged Owner registration ceremony. Registration state is persisted server-side for five minutes and is bound to the initiating Owner session.
- `POST /v1/admin/passkeys/authentication/start` and `/finish` implement passkey authentication with required user verification. Challenges are single-use and expire after five minutes; credential state is updated after successful authentication.
- First enrollment enables `passkey_required`, revokes bootstrap-derived sessions and blocks future bootstrap session exchange. Passkey sessions are explicitly bound to an active credential, so credential revocation invalidates those sessions.
- The remote console now supports browser WebAuthn enrollment and passkey sign-in. Ten random 256-bit recovery codes are issued once, stored only as SHA-256 hashes, and individually consumed by `POST /v1/admin/passkeys/recovery`. Recovery sessions are short-lived for writes; replacement passkey enrollment rotates the recovery-code set.
- Recovery session bindings and ceremony state are persisted in migrations `0011` and `0012`. State serialization is enabled only because the challenge objects are stored server-side in PostgreSQL, never in browser storage.

## Remaining work before Gate 4 closes

1. Execute end-to-end WebAuthn registration, passkey authentication, single-use recovery, recovery rotation and lost-passkey restoration tests against the deployed HTTPS origin and supported browsers/authenticators.
2. Implemented source: Rust applies a bounded eight-failure/60-second lockout keyed by site and effective client IP, with bounded bucket storage. Add production telemetry/abuse review and adversarial load validation.
3. Execute route-by-route instrumentation for five-minute WebAuthn Owner step-up and role separation across every mutation route.
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
