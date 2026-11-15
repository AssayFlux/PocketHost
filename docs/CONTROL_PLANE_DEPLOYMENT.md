# Production Control Plane deployment

The repository includes a manually gated GitHub Actions deployment workflow at `.github/workflows/deploy-control-plane.yml`. It deploys the current `main` branch to an operator-managed Linux host over SSH, starts the Docker Compose stack, and verifies the HTTPS health endpoint.

## Host requirements

- Linux server with Docker Engine and the Docker Compose v2 plugin already installed.
- A non-root deployment account permitted to run Docker Compose.
- Public DNS A/AAAA records for the control-plane hostname pointing to the host.
- Inbound TCP 80/443 reachable so Caddy can issue and renew TLS certificates.
- Git and outbound access to GitHub and container registries.
- Persistent Docker volumes; do not delete `pockethost-control-data` or `pockethost-artifacts`.

## GitHub repository variables

Set these under Settings → Secrets and variables → Actions → Variables:

| Variable | Meaning |
|---|---|
| `POCKETHOST_CONTROL_HOST` | SSH hostname or IP |
| `POCKETHOST_CONTROL_USER` | Non-root deployment user |
| `POCKETHOST_CONTROL_PATH` | Absolute checkout path, e.g. `/opt/pockethost` |
| `POCKETHOST_CONTROL_DOMAIN` | Public API hostname |
| `POCKETHOST_CONTROL_ORIGIN` | Exact HTTPS origin of the TypeScript console |

## GitHub repository secrets

Set these under Settings → Secrets and variables → Actions → Secrets:

| Secret | Meaning |
|---|---|
| `POCKETHOST_CONTROL_SSH_KEY` | Private SSH deploy key |
| `POCKETHOST_CONTROL_KNOWN_HOSTS` | Pre-verified SSH host-key line(s); do not use an unverified key scan |
| `POCKETHOST_CONTROL_ADMIN_TOKEN` | Unique Owner token, at least 64 random hex characters |
| `POCKETHOST_POSTGRES_PASSWORD` | Independent strong PostgreSQL password |

Use a protected GitHub Environment named `production` and require an approval before deployment. Never put these values in repository files or workflow inputs.

## Deploy

After the variables and secrets are configured, open Actions → Deploy PocketHost Control Plane → Run workflow. The workflow checks configuration, connects using the pinned known-hosts file, updates the repository checkout, writes a mode-0600 `.env`, starts the Compose stack and polls `https://DOMAIN/health`.

The deployment workflow does not configure DNS, Cloudflare Access, firewall rules, backups, or the frontend's hosting. Those remain operator-controlled prerequisites. It also does not register a phone automatically; register the device in the console and pair it in PocketHost Advanced → Remote Control Plane.

## Recovery

- Inspect services with `docker compose ps` and `docker compose logs --tail=200 control-plane caddy database`.
- Back up PostgreSQL and the artifact volume together.
- Rotate the Owner token by updating the GitHub secret and redeploying.
- Restore the database and artifact volume from the same backup point to preserve artifact references.
