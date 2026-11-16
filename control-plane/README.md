# PocketHost Remote Control Plane

The Remote Control Plane is an **optional, separately hosted management backend**. It does not serve the websites. The Android phone remains the website origin and maintains outbound HTTPS polling while PocketHost's foreground HostingService is active.

Core local hosting and the phone's Cloudflare Tunnel do not require this backend. The current Docker Compose deployment expects a Linux host; see [the deployment guide](../docs/CONTROL_PLANE_DEPLOYMENT.md). Do not confuse an existing website tunnel with a deployed control-plane API.

## Deploy

1. Review `compose.yaml` and `Caddyfile`. The default Caddy topology expects inbound 80/443 and is not automatically suitable for a Cloudflare Tunnel-only deployment.
2. Copy `.env.example` to `.env`.
3. Set `CONTROL_PLANE_DOMAIN`, set `POCKETHOST_CONTROL_API_HOST` to the same dedicated management hostname, and set the exact HTTPS `POCKETHOST_CONTROL_ORIGIN`. Relay visitor hostnames must be separate from this management hostname; admin and device API routes return 404 when addressed through a Relay visitor hostname.
4. Generate unique high-entropy values for `POCKETHOST_CONTROL_ADMIN_TOKEN` and `POSTGRES_PASSWORD`.
5. Start the stack:

```sh
docker compose --env-file .env -f compose.yaml up -d --build
```

Only Caddy publishes ports in the default topology. PostgreSQL and the Axum service remain on the private Docker network. Persist and back up the PostgreSQL and artifact volumes. Never commit `.env`.

## Register and pair a phone

Use the console's Remote Control Plane panel or call. The console exchanges the returned long-lived API credential for a 12-hour revocable administrative session; management endpoints reject raw API credentials.

```sh
ADMIN_SESSION=$(curl --fail-with-body -sS -X POST "https://$CONTROL_PLANE_DOMAIN/v1/admin/session" \
  -H "Authorization: Bearer $POCKETHOST_CONTROL_ADMIN_TOKEN" | jq -r .accessToken)

curl --fail-with-body -X POST "https://$CONTROL_PLANE_DOMAIN/v1/admin/devices" \
  -H "Authorization: Bearer $ADMIN_SESSION" \
  -H "Content-Type: application/json" \
  -d '{"name":"PocketHost phone"}'
```

The returned device token is displayed once. Enter it in PocketHost → Advanced → Remote Control Plane. The Android app encrypts it using Android Keystore. Start the hosting foreground service to start heartbeat and command polling.

## Supported operations in source

- Register, list and revoke devices.
- Receive device heartbeats and report recent online state (the console uses a 90-second heartbeat threshold).
- Store versioned desired configuration and queue `apply_config`.
- Queue allowlisted site, tunnel, runtime and resource actions.
- Upload a website ZIP (up to 50 MiB); the phone downloads it using its device token, checks SHA-256, validates it and activates a new release.
- Create/revoke Editor and Viewer users; bootstrap token is Owner.
- Exchange the bootstrap/team API credential for a 12-hour server-side administrative session; session tokens are stored hashed, expire, can be explicitly revoked, and are invalidated immediately when the underlying team account is revoked or its API token is rotated.
- Rotate a team-user token immediately (Owner only); the previous token is invalidated in the same transaction as the audit event and the replacement is returned once.
- Rotate a device token immediately; the previous credential is invalidated and the replacement is returned once.
- Read latest 200 audit events.

Supported configuration keys include `pythonMaxWorkers` (2–16), `nodeHeapMb` (96–512) and `tunnelEnabled` (boolean). The phone validates settings before applying them.

## AssayFlux Relay transport

The control plane now includes a first-party Relay gateway path alongside management:

- Owner registers a hostname with `POST /v1/admin/devices/{id}/relay-sites`.
- The API returns a one-time DNS TXT proof at `_pockethost-verification.<hostname>`. A route remains inactive until `POST /v1/admin/relay-sites/{id}/verify` resolves and matches that TXT token.
- The Android device opens an authenticated outbound Server-Sent Events connection at `GET /v1/device/relay/stream`. Visitor requests are queued to that stream; the device forwards them to its local Rust listener and posts bounded responses to `POST /v1/device/relay/responses/{request_id}`.
- The visitor gateway routes only verified hostnames, requires HTTPS at the trusted edge, and binds each hostname to a device and local `site_id`. The Android agent independently checks that the hostname is registered to that exact local site ID before forwarding.
- Device deletion and token rotation close the active Relay session. Responses are bound to the device that owns the pending request.

**Current transport limits:** this is bounded HTTP request/response forwarding, not a general TCP tunnel. Request and response bodies are limited to 8 MiB each, each visitor request has a 45-second deadline, and four local requests run concurrently per device. WebSocket upgrades, streaming downloads/SSE, HTTP/2 end-to-end multiplexing, and long-lived response bodies are not yet supported. For standalone public ingress, the included Caddy profile redirects HTTP to HTTPS and uses on-demand TLS; Caddy asks the control plane before issuing a certificate and only verified Relay hostnames are approved. Point the hostname's A/AAAA record to the Caddy host and open ports 80/443. If using the existing Cloudflare Tunnel profile instead, add each verified public hostname to the same tunnel and route it to the control-plane service. The Relay agent connection from the phone is outbound-only. The phone's local SiteRegistry enforces Public/Private/Restricted visitor credentials; Relay does not bypass that check.

Do not advertise this as a complete general-purpose Relay until streaming/WebSocket support, multi-ABI Android validation, load tests, deployed DNS/TLS validation and reconnect/soak tests pass.

## Security and delivery model

- Admin management routes require a short-lived server-side session bearer. Long-lived bootstrap/team API credentials are accepted only by the session-exchange endpoint. Device APIs continue to use device bearer tokens.
- Device/user tokens are 256-bit random values; token hashes are stored in PostgreSQL.
- Commands are allowlisted; no arbitrary shell or SQL endpoint is exposed.
- Command delivery uses a lease and expiry. The phone persists command-start and final-result markers before acknowledgement. If the process is interrupted mid-action, the agent reports an interrupted result rather than blindly repeating the side effect.
- Device and team-user token rotation invalidates the previous credential immediately; team-user rotation is Owner-only. Team-user token rotation and revocation also revoke all of that user's active administrative sessions.
- Device revocation invalidates subsequent device API calls and removes device-scoped queued data.
- Administrative session creation and revocation are recorded in the same transaction as session state.
- Administrative mutations (user/device lifecycle, configuration, command/deployment queueing, token rotation and command acknowledgement) commit with their audit event in the same database transaction.
- Deployment ZIP blobs are created without overwrite, flushed to storage, and removed automatically if the database transaction fails; blob deletion after successful acknowledgement/revocation is post-commit.
- CORS is restricted to the configured exact console origin.
- Production startup requires POCKETHOST_CONTROL_API_HOST. Admin and device endpoints accept requests only when the HTTP Host matches this dedicated hostname; verified Relay visitor hostnames can reach only the visitor gateway fallback, not the management or device API.
- Keep the API and database private behind the selected TLS/reverse-proxy topology.

## Boundaries and unverified items

- The control plane cannot browse the phone filesystem or execute arbitrary shell commands.
- Delivery is polling-based, not push-based; commands require the phone to be online and HostingService running.
- Audit API returns the latest 200 events; this is not an archival export system.
- Commands expire; an interrupted command may require an operator to submit a fresh action.
- The backend has source/CI tests, but external deployment, TLS, console CORS, real-device pairing and end-to-end command recovery must be verified before production.
- The control plane does not automatically configure Cloudflare DNS, tunnels or Access.
- Do not expose private websites without a visitor access policy such as Cloudflare Access.
