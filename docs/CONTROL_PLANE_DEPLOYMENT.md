# Optional PocketHost Remote Control Plane deployment

## Important: this is not the phone's website server

The Android phone remains the website origin and connects directly to Cloudflare Tunnel. The control plane is a **separate optional management service** for remote device registration, heartbeat, queued commands, team roles and audit history. It does not serve website traffic and is not required for local hosting or the phone's Cloudflare Tunnel connection.

The current deployment workflow uses a Linux host with Docker Compose. This is an additional infrastructure requirement only if the owner chooses to deploy the optional remote-management feature. Do not treat a VPS as a prerequisite for the core PocketHost phone-hosting product.

The owner has confirmed that their website domain and Cloudflare Tunnel are already connected. PR #58 adds an explicit Cloudflare Tunnel ingress profile for the control plane. It can use a connector token for the same named tunnel, but it requires a **separate public hostname** and a Cloudflare Tunnel Public Hostname route targeting `http://control-plane:8080` on the Compose network. This is separate from the phone's website origin. When the optional tunnel secret is absent, the deployment workflow uses the existing Caddy public-TLS profile instead.

## Host requirements for the current Compose deployment

- Linux host with Docker Engine and Docker Compose v2.
- Non-root deployment account allowed to run Docker Compose.
- Persistent Docker volumes for PostgreSQL and uploaded deployment artifacts.
- Outbound access to GitHub and container registries.
- If using the included Caddy public-TLS topology: inbound TCP 80/443 and public DNS for the control-plane hostname.

## GitHub Actions variables

| Variable | Meaning |
|---|---|
| `POCKETHOST_CONTROL_HOST` | SSH hostname or IP |
| `POCKETHOST_CONTROL_USER` | Non-root deployment user |
| `POCKETHOST_CONTROL_PATH` | Absolute checkout path |
| `POCKETHOST_CONTROL_DOMAIN` | Public API hostname |
| `POCKETHOST_CONTROL_ORIGIN` | Exact HTTPS origin of the TypeScript console |

## GitHub Actions secrets

| Secret | Meaning |
|---|---|
| `POCKETHOST_CONTROL_SSH_KEY` | Private SSH deployment key |
| `POCKETHOST_CONTROL_KNOWN_HOSTS` | Pre-verified SSH host-key line(s) |
| `POCKETHOST_CONTROL_ADMIN_TOKEN` | Unique high-entropy Owner token |
| `POCKETHOST_POSTGRES_PASSWORD` | Independent strong PostgreSQL password |
| `POCKETHOST_CONTROL_TUNNEL_TOKEN` | Optional named Cloudflare Tunnel connector token. When set, deployment uses the Cloudflare Tunnel profile and does not publish inbound host ports. |

Put these only in GitHub Actions secrets/variables. Never paste private keys, tokens or passwords into chat, issues or repository files. Use a protected `production` environment and require approval.

## Deployment

1. Review `control-plane/compose.yaml` and `.github/workflows/deploy-control-plane.yml`.
2. Populate the required host, SSH, database and Owner-token variables/secrets.
3. Configure the exact allowed console origin. The deployment workflow writes POCKETHOST_CONTROL_API_HOST from POCKETHOST_CONTROL_DOMAIN; this is the dedicated API hostname and is distinct from POCKETHOST_CONTROL_HOST, which is the SSH host/IP.
4. For Cloudflare mode, route the dedicated management hostname to `http://control-plane:8080`. For each verified Relay visitor hostname, route it to `http://control-plane:8081`; the visitor listener contains no admin or device-management routes. The phone's outbound management/Relay agent connects to the dedicated hostname on port 8080. Add POCKETHOST_CONTROL_TUNNEL_TOKEN as a GitHub Actions secret. The phone's independent Cloudflare website hostname must continue to target the phone's Rust listener, not this service.
5. If the optional tunnel secret is absent, the workflow uses the Caddy public-TLS profile and requires inbound TCP 80/443.
6. Run **Actions → Deploy PocketHost Control Plane → Run workflow**.
7. Confirm `https://<control-plane-domain>/health` returns HTTP 200, admin/device API calls work only on the dedicated management hostname, a Relay visitor hostname cannot access `/v1/admin` or `/v1/device` endpoints, CORS preflight succeeds for the console origin and no unintended ports are reachable.
8. Register a device and pair it in PocketHost → Advanced → Remote Control Plane.
9. Verify heartbeat, live configuration, command queue/acknowledgement, device revocation and audit events.

The workflow does not create DNS records, configure Cloudflare Access, deploy the frontend, register the phone or perform physical-device testing.

## Data and recovery

- Keep PostgreSQL data and the artifact volume in coordinated backups.
- Protect the `.env` file and restrict access to the Docker host.
- Rotate the Owner token through the deployment secret and restart/redeploy.
- Restore the database and artifact volume from the same backup point.
- Do not publish database port 5432 or the raw application port 8080 to the internet.

## Production limitation

The existence of this workflow and backend source does not mean the control plane is deployed. The Cloudflare Tunnel profile removes the inbound-port requirement but still needs a configured hostname route, a Linux Docker host, owner-controlled secrets and end-to-end pairing/command-recovery acceptance. Neither ingress mode is production-approved until the live service and phone agent are tested together.
