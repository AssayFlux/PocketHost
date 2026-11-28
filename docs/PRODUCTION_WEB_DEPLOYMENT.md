# PocketHost production web console and custom-domain relay

This deployment composes three deliberately separate public routes:

- `console.example.com`: static React/Vite management console.
- `api.example.com`: Rust/Axum control-plane API.
- Other verified hostnames: Caddy visitor ingress to the relay listener on port 8081.

The Android management API remains loopback-only on the phone. Do not publish port 17841 to the internet. Its production CORS origins are compiled into the APK as an exact allowlist. Build the APK with `-PpockethostManagementAllowedOrigins=https://console.yourdomain.tld` (comma-separated for multiple exact HTTPS origins) so the installed host accepts the deployed console origin; localhost development origins remain separately permitted. The console's local-device panel is intended for a browser on the same device/network path; use **Remote Control Plane** for remote management through the independently deployed Rust API.

## 1. Configure

From the repository root:

```sh
cp control-plane/.env.example control-plane/.env
```

Edit `control-plane/.env` and set:

- `CONTROL_PLANE_DOMAIN=api.yourdomain.tld`
- `POCKETHOST_CONTROL_API_HOST=api.yourdomain.tld`
- `POCKETHOST_CONSOLE_DOMAIN=console.yourdomain.tld`
- `POCKETHOST_CONTROL_ORIGIN=https://console.yourdomain.tld`
- `POCKETHOST_WEBAUTHN_RP_ID=console.yourdomain.tld`
- `POCKETHOST_CONTROL_ORIGINS=https://console.yourdomain.tld` (add additional exact HTTPS origins comma-separated)
- Strong unique hexadecimal values (at least 64 random hex characters) for the admin token and database password; using hex avoids reserved-character problems in the PostgreSQL connection URL.
- Correct trusted proxy addresses if the Docker subnet is changed.

Do not use wildcard CORS origins. The WebAuthn origin and RP ID must correspond to the browser console origin. The API hostname is separately restricted by `POCKETHOST_CONTROL_API_HOST`.

## 2. DNS

Create DNS records for both console and API hostnames pointing to the public server running this Compose stack:

| Name | Type | Target |
|---|---|---|
| `console` | A | Public IPv4 of the gateway |
| `api` | A | Public IPv4 of the gateway |
| `console` | AAAA | Public IPv6, only if the gateway is reachable over IPv6 |
| `api` | AAAA | Public IPv6, only if the gateway is reachable over IPv6 |

If Cloudflare proxying is enabled, configure SSL/TLS mode to **Full (strict)** and ensure the origin can complete the ACME challenge. Do not publish an AAAA record unless IPv6 routing and firewall access to TCP/80 and TCP/443 are working.

For each visitor hostname, use the hostname challenge shown in Remote Control Plane and point that hostname to this relay gateway. Publish the exact TXT ownership proof before selecting Verify DNS. The hostname remains ineligible for on-demand TLS until the backend records successful verification and the route is enabled.

## 3. Deploy

The public profile starts Caddy; the Cloudflare profile is optional and is not required when DNS and inbound TCP/80+443 reach this server directly.

```sh
cd control-plane
docker compose --env-file .env --profile public config
docker compose --env-file .env --profile public up -d --build
docker compose --env-file .env --profile public ps
docker compose --env-file .env --profile public logs --tail=150 control-plane web-console caddy
```

The console image is built from `web-console/Dockerfile`; its API endpoint is injected at build time from `CONTROL_PLANE_DOMAIN`. Changing the API hostname requires rebuilding the console image.

## 4. Verify before connecting a phone

```sh
curl -fsS https://api.yourdomain.tld/health
curl -fsSI https://console.yourdomain.tld/
docker compose --env-file .env --profile public exec caddy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
```

Also verify from a network outside the server LAN:

- Console HTML and static assets load over HTTPS.
- API health endpoint responds over HTTPS.
- Browser preflight from the configured console origin succeeds.
- An unlisted Origin is not granted `Access-Control-Allow-Origin`.
- Certificates are valid for both console and API hostnames.
- Visitor hostname TLS authorization returns 200 only after DNS ownership verification and route enablement.
- An external visitor can load the intended website through the relay after a phone is enrolled, connected and serving the selected site.

The repository CI workflow checks the console TypeScript/Vite build and container image build. It does not deploy your infrastructure or prove public DNS, TLS, mobile carrier reachability or visitor acceptance.

## 5. Android hosting and visitor path

1. Install and start PocketHost on the Android host.
2. Create/import a site and confirm it is enabled locally.
3. Enroll the Android device with the Rust control plane.
4. Issue the dedicated Relay host credential and configure the phone's **Settings → Network → AssayFlux Relay**.
5. In Remote Control Plane, bind the visitor hostname to the device and exact local site ID.
6. Publish the TXT challenge, verify it, and enable the hostname.
7. Confirm the phone's outbound relay stream is connected.
8. Test the hostname from mobile data or another external network.

The relay avoids requiring inbound connections to the Android phone, but the phone must remain powered, connected and allowed to run its foreground hosting service. The public gateway itself still needs stable inbound reachability. A phone behind CGNAT is not made directly reachable by publishing its mobile IP.

## Security and operational boundaries

- Never expose Android port 17841 publicly.
- Keep PostgreSQL private to the Compose network.
- Keep the admin bootstrap token and database password out of source control.
- Use exact HTTPS CORS origins only.
- Back up PostgreSQL and Caddy's `/data` and `/config` volumes securely.
- Monitor certificate issuance, relay connectivity, device heartbeat and DNS state.
- Do not interpret a successful image build or `/health` response as end-to-end website acceptance.
