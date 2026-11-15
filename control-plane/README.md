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

- Read registered devices and their latest reported runtime state (the web console treats a heartbeat younger than 90 seconds as online).
- Upload a website ZIP (maximum 50 MiB) to a device; the phone downloads it using its authenticated token, verifies SHA-256, validates ZIP paths and atomically activates the release without stopping the Rust static server.
- Create named Editor and Viewer users with one-time bearer tokens. The bootstrap administrator token is the Owner; Editors can change device state, Viewers are read-only. Owners can revoke team tokens.
- Store and version desired device configuration.
- Queue site start/stop, tunnel start/stop and runtime restart commands.
- Receive command acknowledgements and sanitized results.
- Revoke a device by deleting its registry entry.
- Review the latest 200 audit events covering user/device lifecycle, configuration changes, command queueing/acknowledgement and website deployment.

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

## Team roles

The bootstrap administrator token is the Owner. From the Remote Control Plane panel, an Owner can create named Editor and Viewer credentials. The plaintext token is returned once; store it in a password manager and distribute it securely. Only SHA-256 token hashes are stored. Revoke users from the same panel. Editors can manage devices, configuration, commands and website deployments. Viewers can inspect devices, desired configuration and audit history but cannot mutate anything. The Owner-only user endpoints are GET/POST /v1/admin/users and DELETE /v1/admin/users/{id}.

## Remote website deployment

The web console accepts a ZIP up to 50 MiB containing index.html at the archive root or in one top-level folder. The backend stores the archive in the dedicated pockethost-artifacts volume and queues a device-scoped deployment. The phone downloads the archive using its own bearer token, checks declared size and SHA-256, then runs the same traversal-safe ZIP importer used by local deployment. The importer preserves the previous release and swaps the active site directory; the Rust server remains running. Deployment does not allow shell hooks or package installation.

## Operational validation

Run the PocketHost quality workflow for source and backend integration checks. Dispatch .github/workflows/android-integration.yml with run_emulators=true to build ABI-specific debug packages and run Android instrumentation on API 26, 33 and 35 x86_64 emulators. This does not replace testing on real ARMv7/ARM64 phones, vendor battery-management behavior, or an actual Cloudflare-managed public hostname. Follow docs/REMOTE_CONTROL_ACCEPTANCE.md for the full release gate.