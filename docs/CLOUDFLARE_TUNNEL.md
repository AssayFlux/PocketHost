# Cloudflare Tunnel on Android

## Purpose and current project configuration

PocketHost uses an Android-targeted Cloudflare Tunnel connector to provide outbound connectivity from the phone-hosted Rust website origin. The project owner has confirmed that the domain and Cloudflare Tunnel are already connected. **Reuse the existing named tunnel and Public Hostname; do not create a duplicate tunnel.**

This guide is for checking and connecting the existing configuration to PocketHost, not for assuming that DNS or tunnel configuration is automatically managed by the app.

## Connector implementation

- Upstream source: `cloudflare/cloudflared`
- Pinned source tag: `2026.11.0`
- Android ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- NDK minimum API: 26
- Build script: `scripts/build-cloudflared-android.sh`
- Runtime: `cloudflared tunnel --no-autoupdate --loglevel info --metrics 127.0.0.1:20241 run`
- Credential channel: `TUNNEL_TOKEN` environment variable, not a command-line argument
- Token storage: AES-256-GCM with an Android Keystore key

The connector is built from upstream source for Android; it is not a Cloudflare-published Android binary. Validate each supported ABI and Android version.

## Connect the existing tunnel

1. In Cloudflare Zero Trust, open the existing named tunnel.
2. Confirm the connector token belongs to that tunnel. Treat it as a password.
3. In PocketHost Tunnel settings, provide the token through the app's intended secure input. Do not paste it into chat, source files, screenshots or logs.
4. In the tunnel's Public Hostname configuration, confirm the hostname routes to the phone's Rust website listener and correct local port. Do not point it at the management API.
5. Start PocketHost hosting, then start/enable the tunnel from the app.
6. Verify the hostname from mobile data or another independent network and confirm the expected website content is returned over valid HTTPS.

If the existing tunnel already has a working hostname, preserve its Cloudflare configuration and only adjust its origin when needed to match the PocketHost listener.

## Separate connection states

Do not conflate:
- **Token configured:** the app has encrypted credentials.
- **Connector running/ready:** the local cloudflared process is alive and its local readiness check succeeds.
- **Hostname externally verified:** a request from outside the phone's network resolves, completes TLS and returns the intended site.

Only the third state proves public website reachability.

## Security and limitations

- A tunnel token grants access to the named tunnel. Rotate it in Cloudflare if exposed.
- At-rest encryption does not protect a rooted/compromised device or token memory while cloudflared runs.
- Do not log the token or put it in process arguments.
- A public hostname exposes its configured origin. PocketHost static hosting has no built-in visitor authentication; use Cloudflare Access for private content.
- Do not expose the Android management API or its port through a Public Hostname.
- Tunnel uptime depends on Cloudflare, the phone network and Android process/background policy.
- Tunnel connectivity is not an uptime guarantee and does not make the phone a high-availability server.

## Validation checklist

- [ ] Existing named tunnel and hostname identified; no duplicate created.
- [ ] Correct tunnel token saved through PocketHost settings.
- [ ] Token is encrypted at rest and absent from logs/diagnostics.
- [ ] cloudflared process reaches local ready state.
- [ ] Public Hostname targets the Rust website listener only.
- [ ] External HTTPS request returns the expected site.
- [ ] Unknown hostname and management API exposure checks pass.
- [ ] Wi-Fi/mobile transition and connector reconnect pass.
- [ ] Stop/restart, screen-off, battery saver and process-death behavior pass on physical devices.
- [ ] Cloudflare Access is verified for any private content.
