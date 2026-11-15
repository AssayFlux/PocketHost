# AssayFlux Pocket Host

**A native Android website host. Your phone is the origin.**

AssayFlux Pocket Host turns an Android phone into a small website host. It runs a Rust HTTP engine on-device, serves static sites, and can route owner-installed Python WSGI and Node.js applications. Cloudflare Tunnel can expose the phone's website without opening an inbound router port.

No Termux. No root. No mandatory VPS. The existing domain and named Cloudflare Tunnel can be reused.

> **Current status:** integrated development build; PRs #48–#62 are merged. PR #62 quality CI [43769117084](https://github.com/AssayFlux/PocketHost/actions/runs/43769117084) passed all six jobs on its head and was merged as `eee3b82d8071fdb06acb4cea0393dfebab2fc869`. Fresh main quality validation [43769131802](https://github.com/AssayFlux/PocketHost/actions/runs/43769131802) is running. Main Android integration [43769131790](https://github.com/AssayFlux/PocketHost/actions/runs/43769131790) has built cloudflared and is assembling the ABI APKs; emulator smoke is skipped for push events. The PR-triggered API 26/33/35 run [43769116977](https://github.com/AssayFlux/PocketHost/actions/runs/43769116977) is pending. The last available main APK artifact, [14168200339](https://github.com/AssayFlux/PocketHost/actions/runs/43728985708/artifacts/14168200339), predates PR #62 and must not be treated as the latest merged build. This is not a signed production release. Emulator and physical-device acceptance remain open. This is not a production-approved or signed release.

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
- Latest main: `05afe2041c80ace4bddbd0711e554f40e32aa6a6`
- [Quality CI](https://github.com/AssayFlux/PocketHost/actions/runs/43725437551) passed all six jobs on the implementation through the custom-root depth fix; the latest main validation is [43726081859](https://github.com/AssayFlux/PocketHost/actions/runs/43726081859).
- [Android integration build](https://github.com/AssayFlux/PocketHost/actions/runs/43725437477) is building the current main app source for ARM64, ARMv7 and x86_64. The backup-specific API 26/33/35 emulator matrix is tracked separately in [run 43724908863](https://github.com/AssayFlux/PocketHost/actions/runs/43724908863); physical-device acceptance is owner-run.
- Debug APKs are not signed production releases. Release signing requires owner-controlled signing material.

See [PHASE_0_STATUS.md](docs/PHASE_0_STATUS.md) for exact validation evidence and outstanding gates.

## Security boundaries and known gaps

- Python and Node hosting are supported features for **owner-installed applications**. Visitors can access those hosted sites through normal HTTP routing; visitor-submitted executable code is not a supported workflow.
- Python and Node currently run under the app UID. A separate process is not an OS sandbox. Only run code you trust; proper OS-backed runtime isolation, filesystem/network confinement and enforceable resource quotas remain release blockers.
- The management API is loopback-only and authenticated. Do not expose it through Cloudflare.
- Full backups exclude device-bound credentials and common secret files. Secrets embedded in ordinary source/config files cannot be detected reliably; review project contents before sharing a backup.
- Route manifest v3 now includes registered custom static roots under app-private storage. Custom Python/Node route roots outside their configured project roots remain unsupported.
- The optional remote control-plane source is not proof of a deployed backend or a paired device.
- Payment-provider integration and independent security review are not complete.
- Physical Android 8/15 device acceptance, public HTTPS verification, sustained thermal/battery tests and OEM lifecycle checks remain outstanding.

Pocket Host is intended for small sites, development and modest workloads—not cloud-region uptime or unbounded public application execution.
