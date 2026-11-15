# PocketHost Remote Management Deployment

This is an independently hosted management service. It does not host website traffic. The Android phone remains the website origin and maintains an outbound HTTPS polling connection while the hosting foreground service is running.

## Deploy with Docker Compose

1. Copy `.env.example` to `.env`.
2. Set `CONTROL_PLANE_DOMAIN` to a DNS name pointing to this server.
3. Set `POCKETHOST_CONTROL_ORIGIN` to the exact HTTPS origin where the web console is hosted.
4. Generate independent high-entropy values for `POCKETHOST_CONTROL_ADMIN_TOKEN` and `POSTGRES_PASSWORD`.
5. Run:

```sh
docker compose --env-file .env -f compose.yaml up -d --build
```

Caddy obtains and renews the public TLS certificate. The API and PostgreSQL ports are not published directly to the host; only Caddy exposes 80/443. Persist the Docker volumes and back them up securely. Never commit `.env`.

## Register a phone

Use the web console's Remote Control Plane section or call the API:

```sh
curl --fail-with-body -X POST "https://$CONTROL_PLANE_DOMAIN/v1/admin/devices" \
  -H "Authorization: Bearer $POCKETHOST_CONTROL_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"PocketHost phone"}'
```

Copy the returned `device_token` into PocketHost → Advanced → Remote Control Plane. The token is shown once and encrypted on the phone with Android Keystore. Start hosting; the foreground service then begins outbound heartbeat and command polling.

## Supported remote operations

- Read registered devices and their latest reported runtime state.
- Store and version desired device configuration.
- Queue site start/stop, tunnel start/stop and runtime restart commands.
- Receive command acknowledgements and sanitized results.
- Revoke a device by deleting its registry entry.

The current device configuration contract supports `pythonMaxWorkers` (2–16), `nodeHeapMb` (96–512) and `tunnelEnabled` (boolean). The phone refuses to apply unsupported or invalid settings. Resource ceilings may require the relevant runtimes to be stopped before applying.

## Delivery and security model

- Administrator and device APIs use bearer authentication.
- Device tokens are 256-bit random values; only SHA-256 hashes are stored in PostgreSQL.
- Device commands are allowlisted; arbitrary shell and SQL execution are not exposed.
- Command delivery uses a two-minute lease. A device persists the last 100 command results before acknowledging, preventing a retried restart command from running twice in ordinary process restarts.
- A deleted device loses its token immediately and its queued commands/configuration are cascade-deleted.
- Browser CORS is restricted to the exact `POCKETHOST_CONTROL_ORIGIN`.
- Caddy terminates HTTPS; keep the application and database on the private Docker network.
- Rotate the administrator token through deployment secrets and restart the service.

## Boundaries

The control plane cannot remotely access the phone's filesystem. Site archive upload/deployment, account-level multi-user roles, audit export, push delivery, and Android-side offline command expiry are not yet implemented. The phone must be online, the hosting service running, and Android background execution permitted for remote commands to be processed. Do not expose private websites without a visitor access policy such as Cloudflare Access.
