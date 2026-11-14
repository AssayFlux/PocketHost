# Cloudflare Tunnel on Android

**Status:** Android connector integration in development. A successful source build is not proof of device compatibility or public reachability.

## Design

PocketHost builds upstream cloudflared source at a pinned release tag for Android using the Android NDK. The resulting executable is packaged per ABI and launched as an app-owned child process from the user-started foreground service.

The tunnel token is encrypted with AES-256-GCM using a non-exportable Android Keystore key. It is supplied to the child process using the TUNNEL_TOKEN environment variable, not a command-line argument. The connector uses a loopback metrics/readiness endpoint and the static origin remains bound to loopback by default.

## Current connector target

- Upstream source: cloudflare/cloudflared
- Pinned source tag: 2026.11.0
- Android ABIs: arm64-v8a, armeabi-v7a, x86_64
- Minimum API passed to NDK toolchains: 26
- Build script: scripts/build-cloudflared-android.sh
- Runtime command: cloudflared tunnel --no-autoupdate --loglevel info --metrics 127.0.0.1:20241 run
- Secret channel: TUNNEL_TOKEN environment variable

## Cloudflare setup

1. In Cloudflare Zero Trust, create a remotely managed named tunnel.
2. Copy its connector token and save it in PocketHost's Tunnel settings.
3. Configure a Public Hostname in the Cloudflare dashboard.
4. Set the origin service to http://127.0.0.1:8080 (or the static server port configured in PocketHost).
5. Start PocketHost hosting and enable the tunnel.
6. Confirm the connector reports readiness, then verify the hostname from a separate network.

PocketHost does not create Cloudflare accounts, named tunnels, DNS records or hostnames automatically. These are configured in the Cloudflare dashboard.

## Security and limitations

- A tunnel token grants access to the named tunnel. Treat it as a password.
- Token encryption protects stored-at-rest data; it does not protect a rooted or compromised device or memory while the connector runs.
- Do not log the token or include it in process arguments.
- The tunnel exposes the configured origin to the public internet. There is no visitor authentication in PocketHost's current static server. Add Cloudflare Access or an equivalent policy before exposing private content.
- Tunnel availability depends on Cloudflare's service, the device network and Android background execution policy.
- The connector is an Android-targeted source build, not a Cloudflare-published Android binary. Test each ABI and supported Android version on physical hardware before calling it production-ready.
- The management console is not connected to this local tunnel control path.

## Validation required

- Build and inspect all Android ELF binaries.
- Install and execute cloudflared version on API 26, 33 and 35 emulators.
- Verify token redaction, encrypted persistence and token deletion.
- Establish a named tunnel and check the local /ready endpoint.
- Verify the configured public hostname from mobile data or another external network.
- Exercise Wi-Fi loss/reconnect, process death, stop/restart, battery saver and thermal pressure on representative ARMv7 and ARM64 phones.
