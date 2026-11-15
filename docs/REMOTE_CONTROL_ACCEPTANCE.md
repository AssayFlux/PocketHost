# PocketHost integration and release acceptance

This checklist separates automated evidence from tests that require real infrastructure or physical phones. A green source-compile job is not a substitute for these acceptance tests.

## 1. Remote Control Plane deployment

Prerequisites: a Linux host with Docker Engine and Compose, a DNS name for the control API, and an HTTPS origin for the TypeScript console.

1. Copy `control-plane/.env.example` to `control-plane/.env`.
2. Set `CONTROL_PLANE_DOMAIN`, `POCKETHOST_CONTROL_ORIGIN`, a unique random `POCKETHOST_CONTROL_ADMIN_TOKEN` (at least 64 random hex characters), and a strong independent PostgreSQL password.
3. Run `docker compose --env-file .env -f compose.yaml up -d --build` from `control-plane/`.
4. Verify `https://$CONTROL_PLANE_DOMAIN/health` returns HTTP 200 and the expected service/API version.
5. Verify ports 5432 and 8080 are not published publicly; only 80/443 terminate at Caddy.
6. Verify the certificate chain and hostname using a browser and `curl --fail --show-error https://$CONTROL_PLANE_DOMAIN/health`.
7. Restart the stack and confirm device records, audit history and uploaded artifacts persist.

Do not mark deployment complete until the API hostname is publicly reachable and the exact console origin passes CORS preflight.

## 2. Real Android device pairing

1. Install a debug/release build signed by the intended release key on a physical device.
2. Register a device in the Remote Control Plane console and copy the one-time device token directly into PocketHost Advanced → Remote Control Plane.
3. Confirm the token is not visible in plaintext SharedPreferences or app logs.
4. Start PocketHost hosting and keep the app's foreground service active.
5. Confirm the device appears online (heartbeat within 90 seconds), and that Android reports static, Python, Node and tunnel states accurately.
6. Turn off Wi-Fi/mobile data for at least three minutes. Confirm the UI marks the phone offline and does not claim queued commands succeeded.
7. Restore connectivity. Confirm heartbeat resumes and queued commands are delivered and acknowledged.
8. Revoke the device from the console. Confirm subsequent heartbeat/poll requests return HTTP 401 and no further command is executed.

## 3. Live configuration while serving traffic

1. Deploy a test static site and verify a repeated HTTP GET.
2. Start Python and Node test sites where the device has enough memory.
3. While requests continue to the static site, change Python worker and Node heap limits remotely.
4. Confirm the API reports the new values, static hosting remains RUNNING, and only affected runtime processes restart.
5. Confirm Python and Node supervisors relaunch their processes and the new limits are reflected in runtime diagnostics.
6. Force a runtime startup failure and confirm bounded supervisor backoff without stopping the static origin.

## 4. Remote archive deployment and recovery

1. Upload a valid ZIP (index.html at root) below 50 MiB.
2. Confirm the device downloads it over its authenticated outbound connection, verifies SHA-256 and acknowledges deployment.
3. Repeatedly request the public/static origin while deployment happens; confirm the origin remains available and eventually serves the new release.
4. Upload invalid ZIP data, a traversal entry (`../`), a symlink/path escape, missing index.html, and an archive exceeding the limit. Confirm all are rejected and the active release remains unchanged.
5. Disconnect the phone after command delivery but before acknowledgement. Confirm the device's persisted command result prevents repeating completed actions after the lease expires.
6. Kill the app during a command. Confirm an interrupted command is reported as requiring operator retry rather than silently re-executed.
7. Confirm the previous release can be restored locally after a bad deployment.

## 5. Multi-user and audit acceptance

1. Authenticate with the bootstrap Owner token and create one Editor and one Viewer.
2. Confirm only the Owner can list/create/revoke users.
3. Confirm Editor can register/revoke devices, change configuration, queue commands and deploy archives.
4. Confirm Viewer can list devices, read configuration and audit history, but receives HTTP 403 for every mutation.
5. Revoke a team user and confirm its token immediately returns HTTP 401.
6. Confirm audit events capture actor role, action, target and timestamp for device registration/revocation, config updates, command queue/ack and site deployment.
7. Confirm tokens and secret values never appear in audit details or application logs.

## 6. Android compatibility matrix

Run `.github/workflows/android-integration.yml` with `run_emulators=true`. It builds ABI-specific debug packages for ARM64 and ARMv7 and runs instrumentation on API 26, 33 and 35 x86_64 emulators.

Physical-device acceptance is still required for each ABI family:

| Device class | Required validation |
|---|---|
| Android 8 / API 26, ARMv7, 2–4 GB RAM | Install, foreground service, static HTTP, Python/Node startup, tunnel process, screen-off and recovery |
| Android 8+ / API 26+, ARM64, 4 GB RAM | Same tests plus remote pairing, archive deploy, runtime restart and memory pressure |
| Android 13 / API 33+ | Notification permission, foreground service restrictions, battery saver and background recovery |
| Android 15 / API 35 | Current target behavior, foreground service start/stop, network changes and process recreation |

Record exact model, Android build, ABI, RAM, app version, APK SHA-256, test date and pass/fail for every device. An emulator is not a physical-device substitute. Vendor-specific background restrictions must be checked on the actual Vivo Y95 and Moto G54 devices.

## 7. Cloudflare public hostname and security

1. Create a Cloudflare-managed named tunnel and configure the public hostname to point to the phone's local static origin (for example, `http://127.0.0.1:8080`).
2. Configure DNS and ingress in Cloudflare Zero Trust; do not expose the phone's management API port.
3. Add Cloudflare Access for private sites and administrative origins. Public sites must be intentionally public.
4. Confirm the hostname resolves, TLS validates, and a request returns the expected site content.
5. Confirm unknown hostnames are rejected, static path traversal is blocked, and management API is not reachable through the public hostname.
6. Rotate the tunnel token and confirm the old token no longer connects.
7. Test tunnel disconnect/reconnect, phone network switching, screen-off, and Android process death.
8. Inspect Cloudflare logs and PocketHost logs for leaked tokens; none may appear.

## Evidence and release decision

Attach CI run URLs, backend deployment health output, emulator instrumentation results, device test records, DNS/TLS observations and a redacted audit sample to the release record. Mark each gate as PASS, FAIL or NOT TESTED. Never convert NOT TESTED into PASS.
