# AssayFlux Pocket Host

**A native Android website host. Your phone is the origin.**

AssayFlux Pocket Host turns an Android phone into a small website host. It runs a Rust HTTP engine on-device, serves static sites, and can route owner-installed Python WSGI and Node.js applications. Cloudflare Tunnel can expose the phone's website without opening an inbound router port.

No Termux. No root. No mandatory VPS. The existing domain and named Cloudflare Tunnel can be reused.

> **Current status — 2026-11-16:** reviewed source commit `6a2a54d76e8ae71c8abc4cc9d5f6f0e20a03b34f`; the quality checks and three-ABI host plus companion debug APK packaging passed. API 26 instrumentation executed 31 tests: 28 passed, including the companion UID/loopback test, and 3 failed (two management API timeouts under concurrent PBKDF2 load and one Dashboard accessibility-selector mismatch). The test harness has been adjusted and requires a fresh run. API 33/35 are not yet validated on this exact source. Python and Node still execute under the host app UID; the companion is a capability prototype, not a production runtime sandbox. No production-ready or signed-release claim is made. See [verified project status](docs/PROJECT_STATUS_2026-11-16.md) for evidence.

## What is included

- **Static hosting:** folder/ZIP import, staged deployment, safe path checks, retained releases and rollback.
- **Python:** embedded CPython 3.11 with a WSGI entry point.
- **Node.js:** pinned Node.js 24 mobile runtime with prebundled dependencies.
- **HTTP engine:** Rust + Tokio + Hyper, hostname routing, streaming, byte ranges, MIME handling and bounded admission.
- **Capacity protection:** active-connection ceiling, bounded waiting queue, per-client rate limiting, per-site concurrency caps and graceful shutdown.
- **Cloudflare:** Android-targeted cloudflared connector with encrypted token storage and lifecycle supervision.
- **Local management:** authenticated loopback API and paired TypeScript/React console.
- **Operations:** traffic and device metrics, activity logs, diagnostics and Capacity Lab.
- **Backup:** passphrase-encrypted full-app backup for the active website, registered custom static roots, configured Python/Node project roots, hostname routes and selected non-secret settings. Device-bound credentials are excluded; restored runtimes remain stopped pending owner approval.

## App navigation

The Android app uses five focused destinations:

| Screen | Purpose |
|---|---|
| **Dashboard** | Hosting control, traffic, capacity and device health |
| **Sites** | Website files, import/deploy and backup/restore |
| **Activity** | Filterable operational events and exports |
| **Learn** | Collapsible setup and troubleshooting guides |
| **Settings** | General, Network, Runtimes and About sections |

The visual system uses a restrained navy, ivory and neutral palette, compact cards, clear status hierarchy and Simple/Advanced modes.

## Request flow

```text
Visitor
  │ HTTPS
  ▼
Cloudflare edge
  │ existing outbound tunnel
  ▼
Pocket Host on Android
  │
  ├── Rust HTTP engine ── static website files
  │
  ├── loopback proxy ──── Python WSGI runtime
  │
  └── loopback proxy ──── Node.js runtime
```

The public hostname must point to the Rust website listener. It must never point to the management API. Local and remote management are separate from visitor traffic.

## Platform targets

- Android 8.0 / API 26 and newer.
- ARM64 and ARMv7 phones; x86_64 build target for emulator use.
- 4 GB RAM is the baseline design target, not a promise of a universal visitor count.
- The phone remains subject to Android lifecycle, battery, thermal and OEM restrictions.

## Build and validation

- Repository: [AssayFlux/PocketHost](https://github.com/AssayFlux/PocketHost)
- Latest reviewed main: `6a2a54d76e8ae71c8abc4cc9d5f6f0e20a03b34f`.
- Quality workflow [43786695783](https://github.com/AssayFlux/PocketHost/actions/runs/43786695783) passed all six jobs.
- Android integration [43786695821](https://github.com/AssayFlux/PocketHost/actions/runs/43786695821): cloudflared build and three-ABI host + companion debug APK packaging passed; API 26 emulator is running and API 33/35 are queued. Do not infer emulator success until the jobs finish.
- [Debug APK artifact](https://github.com/AssayFlux/PocketHost/actions/runs/43786695821/artifacts/14190833098) is available. It is not a production release.
- See [verified project status](docs/PROJECT_STATUS_2026-11-16.md), [roadmap](docs/ROADMAP.md) and [phase status](docs/PHASE_0_STATUS.md) for exact boundaries and remaining gates.

## Security boundaries and known gaps

- Python and Node hosting are supported features for **owner-installed applications**. Visitors can access those hosted sites through normal HTTP routing; visitor-submitted executable code is not a supported workflow.
- Python and Node project roots are now canonicalized and restricted to descendants of PocketHost's app-private directories, including symlink-escape rejection. However, both runtimes still execute with the app UID. The new separate-UID companion package is only a capability probe and does not yet host Python or Node. Path validation and a separate process are not an OS sandbox. Only run trusted code until the runtime migration, network confinement and security review are complete.
- The management API is loopback-only and authenticated. Do not expose it through Cloudflare.
- Full backups exclude device-bound credentials and common secret files. Secrets embedded in ordinary source/config files cannot be detected reliably; review project contents before sharing a backup.
- Route manifest v3 now includes registered custom static roots under app-private storage. Custom Python/Node route roots outside their configured project roots remain unsupported.
- The optional remote control-plane source is not proof of a deployed backend or a paired device.
- Payment-provider integration and independent security review are not complete.
- Physical Android 8/15 device acceptance, public HTTPS verification, sustained thermal/battery tests and OEM lifecycle checks remain outstanding.

Pocket Host is intended for small sites, development and modest workloads—not cloud-region uptime or unbounded public application execution.
