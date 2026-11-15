# PocketHost Remote Control Plane

The Remote Control Plane is an **optional, separately hosted management backend**. It does not serve the websites. The Android phone remains the website origin and maintains outbound HTTPS polling while PocketHost's foreground HostingService is active.

Core local hosting and the phone's Cloudflare Tunnel do not require this backend. The current Docker Compose deployment expects a Linux host; see [the deployment guide](../docs/CONTROL_PLANE_DEPLOYMENT.md). Do not confuse an existing website tunnel with a deployed control-plane API.

## Deploy

1. Review `compose.yaml` and `Caddyfile`. The default Caddy topology expects inbound 80/443 and is not automatically suitable for a Cloudflare Tunnel-only deployment.
2. Copy `.env.example` to `.env`.
3. Set `CONTROL_PLANE_DOMAIN` and the exact HTTPS `POCKETHOST_CONTROL_ORIGIN`.
4. Generate unique high-entropy values for `POCKETHOST_CONTROL_ADMIN_TOKEN` and `POSTGRES_PASSWORD`.
5. Start the stack:

```sh
docker compose --env-file .env -f compose.yaml up -d --build
```

Only Caddy publishes ports in the default topology. PostgreSQL and the Axum service remain on the private Docker network. Persist and back up the PostgreSQL and artifact volumes. Never commit `.env`.

## Register and pair a phone

Use the console's Remote Control Plane panel or call:

```sh
curl --fail-with-body -X POST "https://$CONTROL_PLANE_DOMAIN/v1/admin/devices" \
  -H "Authorization: Bearer $POCKETHOST_CONTROL_ADMIN_TOKEN" \
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
- Read latest 200 audit events.

Supported configuration keys include `pythonMaxWorkers` (2–16), `nodeHeapMb` (96–512) and `tunnelEnabled` (boolean). The phone validates settings before applying them.

## Security and delivery model

- Admin and device APIs use bearer authentication.
- Device/user tokens are 256-bit random values; token hashes are stored in PostgreSQL.
- Commands are allowlisted; no arbitrary shell or SQL endpoint is exposed.
- Command delivery uses a lease and expiry. The phone persists command-start and final-result markers before acknowledgement. If the process is interrupted mid-action, the agent reports an interrupted result rather than blindly repeating the side effect.
- Device revocation invalidates subsequent device API calls and removes device-scoped queued data.
- CORS is restricted to the configured exact console origin.
- Keep the API and database private behind the selected TLS/reverse-proxy topology.

## Boundaries and unverified items

- The control plane cannot browse the phone filesystem or execute arbitrary shell commands.
- Delivery is polling-based, not push-based; commands require the phone to be online and HostingService running.
- Audit API returns the latest 200 events; this is not an archival export system.
- Commands expire; an interrupted command may require an operator to submit a fresh action.
- The backend has source/CI tests, but external deployment, TLS, console CORS, real-device pairing and end-to-end command recovery must be verified before production.
- The control plane does not automatically configure Cloudflare DNS, tunnels or Access.
- Do not expose private websites without a visitor access policy such as Cloudflare Access.
