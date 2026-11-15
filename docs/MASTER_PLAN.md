# AssayFlux PocketHost — Master Engineering Plan

**Product:** AssayFlux PocketHost  
**Steward:** AssayFlux  
**Developer identity:** Designed by AssayFlux  
**Package:** `com.assayflux.pockethost`

## 1. Product definition

PocketHost is a standalone native Android application that hosts websites from the phone itself. It is designed for Android 8.0 (API 26)+, a 4 GB RAM baseline and ARMv7/ARM64 phones, without Termux or root.

The phone is the origin server. Rust is the central HTTP engine. Kotlin owns Android lifecycle and the native UI. Cloudflare Tunnel provides outbound public connectivity. Python WSGI and Node.js are optional trusted application runtimes. A TypeScript/React console manages the local device. An independently hosted Rust/PostgreSQL service can optionally provide remote management.

The existing domain and Cloudflare Tunnel are already configured by the project owner. The implementation should reuse that setup and verify the hostname-to-origin mapping; it must not instruct the owner to create a duplicate tunnel.

## 2. Product outcomes

### Simple Mode
- Import a static website from a folder or ZIP.
- Validate and stage the deployment before activation.
- Preview locally, start/stop hosting and see understandable service state.
- Connect through the configured Cloudflare Tunnel and verify the public hostname.
- Update and roll back without losing the active release.

### Advanced Mode
- Manage multiple sites and hostnames.
- Configure static, Python WSGI and Node.js routes.
- Inspect per-source metrics, logs and health.
- Adjust bounded worker and V8 heap settings.
- Manage tunnel state, encrypted credentials, backup and diagnostics.
- Pair the TypeScript console with the local API.
- Optionally pair the device with the remote control plane.

## 3. Technology responsibilities

| Layer | Technology | Ownership |
|---|---|---|
| Android UI | Kotlin + Jetpack Compose | UX, permissions, SAF, lifecycle |
| Background operation | Android Foreground Service | User-visible hosting lifecycle |
| Central hosting engine | Rust | HTTP serving, hostname routing, reverse proxy, request policy and metrics |
| Async runtime | Tokio | Engine concurrency |
| HTTP stack | Hyper | Static HTTP and runtime proxy |
| Kotlin/Rust boundary | JNI | Coarse-grained lifecycle/configuration/status calls |
| Local console | React + TypeScript + Vite | Browser-based device management |
| Python | CPython 3.11 via Chaquopy | One trusted WSGI app at a time |
| Node.js | Pinned Node 24.22.1 mobile SDK | One trusted app in dedicated `:node` process |
| Tunnel | Android-targeted cloudflared | Outbound Cloudflare connection |
| Optional remote control plane | Rust/Axum + PostgreSQL | Device registry, commands, team roles and audit |
| Secrets | Android Keystore + encrypted storage | Device-bound token protection |

TypeScript is the authored language for the web console. Browser builds emit JavaScript as required by browsers; that does not change the TypeScript source policy.

## 4. Request and control paths

### Website request path

```text
Visitor
  -> Cloudflare DNS / HTTPS edge
  -> existing named Cloudflare Tunnel
  -> phone-local Rust listener
  -> hostname router
  -> static site OR loopback Python WSGI OR loopback Node app
  -> response through tunnel
```

The Public Hostname must target the Rust website listener. Never route it to the local management API. The management API is loopback-only and uses a separate pairing/session model.

### Local management path

```text
Browser console (allowed origin)
  -> pairing challenge
  -> owner approves six-digit code in Android app
  -> short-lived bearer session
  -> loopback management API
  -> Android service / Rust engine / runtime supervisors
```

### Optional remote management path

```text
TypeScript console
  -> HTTPS
  -> optional Rust/PostgreSQL control plane
  <- outbound HTTPS polling / heartbeat from phone agent
  -> authenticated allowlisted command
  -> on-device validation and execution
  -> acknowledgement and audit event
```

The remote control plane is not the website origin. Current deployment automation requires a separate Linux/Docker host. The basic phone-hosting product must not depend on this service being deployed.

## 5. Runtime contracts and limitations

### Static site
- Rust serves GET/HEAD, streams files, supports byte ranges and MIME types.
- Normalize and validate hostnames and paths.
- Reject traversal and unknown hosts.
- Bound concurrent connections, response sizes and timeouts.
- Keep static hosting independent from Python/Node availability.

### Python WSGI
- CPython 3.11 via Chaquopy.
- Root `wsgi.py` must export callable `application`.
- One active WSGI application; 2–16 bounded worker threads.
- No automatic `pip install`, ASGI/WebSockets or hostile-code sandbox.
- Imported code runs with the PocketHost app UID.

### Node.js
- Pinned `fogtape/nodejs-mobile` `v24.22.1-0` / upstream Node 24.22.1.
- JNI `node::Start` bridge and isolated Android `:node` service/process.
- One active Node application/process.
- Dependencies must be prebundled; no on-device npm install, PM2, cluster or ordinary desktop child-process workflow.
- TCP listeners are constrained to loopback by the metrics preload; this is not a complete network sandbox.
- V8 old-space limit 96–512 MiB is not a hard total-RSS quota.
- Node projects execute with PocketHost's app UID and must be trusted.

## 6. Security model

- Local hosting listener defaults to loopback; LAN exposure is explicit opt-in.
- Management API is never a public website route.
- Pairing challenges are short-lived, rate-limited and owner-approved.
- Browser sessions are bearer tokens held in sessionStorage, expire after 12 hours and are individually revocable.
- Tunnel and remote device credentials are encrypted with Android Keystore-backed keys.
- Imported archives are size/path/count/depth validated and activated through staged releases.
- Logs and diagnostics must redact credentials and avoid exporting website content.
- Python/Node user code is trusted-only; neither is a hostile-code sandbox.
- Cloudflare Tunnel encrypts the path to the edge but does not authenticate visitors. Use Cloudflare Access for private sites.
- The remote control plane stores token hashes, uses role checks and command allowlists; still requires route-level security tests and deployment hardening.

## 7. Reliability and resource policy

The Android foreground service improves continuity but cannot guarantee 24/7 uptime. OEM battery policies, Doze, thermal throttling, low-memory kills, mobile-network changes and user force-stop remain relevant.

Required engineering behavior:
- Bounded worker/thread and connection counts.
- Explicit timeouts and bounded request/archive sizes.
- Lazy runtime startup and bounded restart backoff.
- Clean stop and process recreation for embedded Node.
- Recover tunnel after transient network loss.
- Preserve active static site when optional runtime configuration changes.
- Redact and rotate operational logs.
- Surface degraded/error states with a safe next action.
- Measure memory, CPU, storage, battery and thermal behavior on representative phones.

## 8. Validation strategy

Validation is staged and evidence-based:
1. Source tests and static checks.
2. Android native/Kotlin compilation.
3. ABI-specific APK packaging and native-library inspection.
4. Emulator instrumentation on API 26, 33 and 35.
5. Physical ARMv7/ARM64 phone testing.
6. External Cloudflare hostname checks from a separate network.
7. Security review and failure/recovery tests.
8. Signed release, upgrade verification and checksums.

A green quality workflow does not imply emulator or physical-device acceptance. An emulator does not replace the Vivo Y95, Moto G54 or an ARMv7 device.

## 9. Repository map

- `app/`: native Android app, services, local API, runtime supervisors and import/backup.
- `rust-engine/`: phone-side Rust hosting engine.
- `web-console/`: TypeScript/React console.
- `control-plane/`: optional remote Rust/PostgreSQL service.
- `tests/`: Python and Node host-side smoke tests.
- `scripts/`: native build helpers.
- `.github/workflows/`: quality, Android integration, signed release and optional control-plane deployment.
- `docs/`: architecture, runtime contracts, deployment and acceptance.

## 10. Current engineering status

See [PHASE_0_STATUS.md](PHASE_0_STATUS.md) for the commit-specific CI state and remaining gates, and [COMPLETION_MILESTONE.md](COMPLETION_MILESTONE.md) for the release checklist. These status documents take precedence over roadmap language in older design discussions.
