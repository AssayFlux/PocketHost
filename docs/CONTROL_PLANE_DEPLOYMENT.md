# Optional PocketHost Remote Control Plane deployment

## Important: this is not the phone's website server

The Android phone remains the website origin and connects directly to Cloudflare Tunnel. The control plane is a **separate optional management service** for remote device registration, heartbeat, queued commands, team roles and audit history. It does not serve website traffic and is not required for local hosting or the phone's Cloudflare Tunnel connection.

The current deployment workflow uses a Linux host with Docker Compose. This is an additional infrastructure requirement only if the owner chooses to deploy the optional remote-management feature. Do not treat a VPS as a prerequisite for the core PocketHost phone-hosting product.

The owner has confirmed that their website domain and Cloudflare Tunnel are already connected. That existing tunnel may be used for the website origin; it does not automatically mean the control-plane API is deployed or routed. If the control plane is to be exposed through Cloudflare, configure a distinct hostname and route it to the control-plane service using an explicitly supported deployment topology. The current Compose file includes Caddy on ports 80/443 and should not be assumed to work unchanged behind an existing tunnel.

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

Put these only in GitHub Actions secrets/variables. Never paste private keys, tokens or passwords into chat, issues or repository files. Use a protected `production` environment and require approval.

## Deployment

1. Review `control-plane/compose.yaml`, `control-plane/Caddyfile` and `.github/workflows/deploy-control-plane.yml` for the intended network topology.
2. Populate the variables and secrets above.
3. Configure the exact allowed console origin.
4. Run **Actions → Deploy PocketHost Control Plane → Run workflow**.
5. Confirm `https://<control-plane-domain>/health` returns HTTP 200, CORS preflight succeeds for the console origin and only intended ports are reachable.
6. Register a device and pair it in PocketHost → Advanced → Remote Control Plane.
7. Verify heartbeat, command queue/acknowledgement, device revocation and audit events.

The workflow does not create DNS records, configure Cloudflare Access, deploy the frontend, register the phone or perform physical-device testing.

## Data and recovery

- Keep PostgreSQL data and the artifact volume in coordinated backups.
- Protect the `.env` file and restrict access to the Docker host.
- Rotate the Owner token through the deployment secret and restart/redeploy.
- Restore the database and artifact volume from the same backup point.
- Do not publish database port 5432 or the raw application port 8080 to the internet.

## Production limitation

The existence of this workflow and backend source does not mean the control plane is deployed. The included Caddy topology is a conventional public-host deployment and is not automatically Cloudflare Tunnel-compatible. A Cloudflare-only/no-inbound-port deployment needs an explicit supported topology and a separate acceptance test before being described as production-ready.
