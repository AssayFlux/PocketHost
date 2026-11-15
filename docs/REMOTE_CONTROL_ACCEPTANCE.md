# PocketHost integration and release acceptance

This checklist records evidence required for a production decision. Use **PASS**, **FAIL** or **NOT TESTED** for every item. Never infer a pass from source presence or an unrelated CI run.

## 1. Current automated CI

- Quality workflow: [quality.yml](../.github/workflows/quality.yml)
- Android integration: [android-integration.yml](../.github/workflows/android-integration.yml)

The quality workflow covers Rust tests, control-plane/PostgreSQL tests, Python syntax and WSGI lifecycle, Node preload smoke, console production build and Android source compilation. The Android integration workflow separately builds cloudflared, assembles ABI-specific APKs and runs API 26/33/35 instrumentation. Record the exact commit SHA and workflow URL in the release evidence.

## 2. Existing Cloudflare domain and tunnel

The project owner has already configured a domain and connected a Cloudflare Tunnel. Reuse the existing named tunnel; do not create a duplicate.

1. Confirm the matching tunnel token is saved in PocketHost Tunnel settings.
2. Confirm the Cloudflare Public Hostname routes to the phone's Rust website listener and correct port (not the Android management API).
3. Start hosting and the connector from PocketHost.
4. From mobile data or another external network, request the configured hostname and verify valid TLS plus the expected website body.
5. Confirm unknown hosts are rejected and the management API is not reachable from the public hostname.
6. Verify Cloudflare Access for private sites; public sites must be intentionally public.
7. Rotate the token and confirm the old token no longer connects if a rotation test is scheduled.
8. Test Wi-Fi/mobile transitions, screen-off, battery saver, connector death and reconnect on real devices.

A green local connector readiness endpoint is not proof of external hostname reachability.

## 3. Local console and management API

1. Pair a browser using the six-digit challenge approved in the Android app.
2. Verify no bearer session exists before explicit pairing.
3. Confirm status, sites, metrics, logs, runtime state and tunnel state match the device.
4. Test invalid/expired pairing challenges, rate limits, wrong codes and repeated attempts.
5. Revoke a session and confirm subsequent requests fail.
6. Restart HostingService and verify old in-memory sessions are no longer valid.
7. Test CORS allowlist and Private Network Access preflight from the actual console origin.
8. Test malformed headers, duplicate Host, oversized headers/body, unsupported transfer encoding and socket timeouts.
9. Verify Host validation against DNS rebinding and unexpected local origins.

## 4. Static hosting and multi-site routing

1. Import a valid folder and ZIP with index.html.
2. Test GET, HEAD, MIME types, byte ranges, large streaming files and missing paths.
3. Register multiple hostnames and verify correct site selection.
4. Test duplicate/invalid hostnames, unknown hosts, traversal attempts, encoded separators and symlink/path escapes.
5. Deploy a new release under traffic; confirm the active server stays available and the previous release is recoverable.
6. Confirm runtime configuration changes do not stop static hosting.

## 5. Python and Node runtime acceptance

### Python
- Confirm CPython 3.11 starts a root `wsgi.py` application exporting `application`.
- Test import validation, missing dependencies, exception paths, stop/restart and supervisor backoff.
- Confirm request metrics and loopback-only behavior by default.
- Stress the WSGI worker ceiling and memory behavior.
- Do not claim ASGI, WebSockets, pip installation or sandbox isolation.

### Node.js
- Load the actual `libnode.so` for the device ABI and confirm `process.version`.
- Import a prebundled dependency, start an HTTP listener and verify Rust reverse-proxy routing.
- Confirm Node TCP listener loopback enforcement.
- Stop the Node service, verify process exit, then start a fresh process and application.
- Test invalid package metadata, missing entrypoint, bundled native modules and resource ceiling behavior.
- Do not claim child-process workflows, on-device npm install or hostile-code sandboxing.

## 6. Optional remote control plane

The remote backend is separate from the phone's website server and optional for core hosting. Only execute this section if remote management is in the release scope.

1. Deploy the Rust/Axum + PostgreSQL service on its selected reachable host/topology.
2. Verify HTTPS health, certificate, exact console CORS origin and database persistence.
3. Register the phone and enter the one-time token in PocketHost Advanced → Remote Control Plane.
4. Confirm heartbeat is received within 90 seconds and state matches the phone.
5. Queue a configuration update; verify it applies while static hosting remains online.
6. Deploy a valid ZIP and verify checksum, activation and acknowledgement.
7. Test invalid ZIP, traversal, wrong checksum, oversize archive and interrupted download.
8. Test disconnect/reconnect, command lease expiry and persisted idempotency result.
9. Test Owner/Editor/Viewer permissions and token revocation.
10. Verify audit events and ensure tokens/secrets never appear in logs or audit details.

The current backend Compose workflow expects Linux/Docker. The default Caddy configuration expects inbound 80/443; it is not automatically compatible with a Cloudflare Tunnel-only network. Confirm the deployment topology before enabling it.

## 7. Physical-device compatibility

Record model, Android build, API, ABI, RAM, app version, APK SHA-256, test date and result.

| Target | Required checks |
|---|---|
| Vivo Y95 · Android 8.1 · 4 GB · ARM64 | Install/upgrade, foreground service, static site, tunnel, screen-off, battery saver, Wi-Fi/mobile transition, Python/Node startup and recovery |
| Moto G54 · Android 15 · ARM64 | Current Android restrictions, notification permission, all runtime workflows, remote pairing if enabled, memory/thermal and process recovery |
| Android 8 / API 26 · ARMv7 · representative 4 GB or lower | ABI install, native libraries, static hosting, tunnel and runtime smoke |
| API 33 and API 35 emulators | CI instrumentation and Android platform behavior |

An emulator is not a physical-device substitute. Foreground services do not guarantee 24/7 uptime; record vendor-specific battery restrictions.

## 8. Backup, diagnostics and release

- Test backup export and restore on a second installation.
- Test wrong passphrase, tampered ciphertext, malformed ZIP and interrupted restore; active site must remain intact.
- Confirm backup scope is static site files only and excludes tunnel credentials, runtime projects, settings and release history.
- Inspect diagnostics for token, password, private key, site contents and personal data leakage.
- Configure release signing secrets outside source control.
- Build signed ABI APKs, verify signatures, install/upgrade, record file sizes and SHA-256, inspect native ABI and 16 KB page-size compatibility.

## Production decision

Production approval requires green CI on the exact release SHA, completed emulator and physical-device tests, external Cloudflare hostname verification, resolved critical/high security findings, and signed/upgrade-tested APKs. If the optional remote control plane is included, its deployed health and real-device command flow are also mandatory. Otherwise mark it explicitly as optional/not deployed.
