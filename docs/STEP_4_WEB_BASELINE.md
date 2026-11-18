# Pocket Host v0.1.0 — Static Hosting Baseline

**Release assignment:** v0.1.0 — First Hosting  
**Scope:** main Android Pocket Host app and bundled Rust static website engine.  
**Not in scope:** Workbench completion, TypeScript compiler, Python/Node server runtime acceptance, fleet workers, multi-host Relay scaling or full fleet control-plane acceptance. Cloudflare Tunnel and the standalone owner-operated AssayFlux Relay Node on Termux/Android are mandatory v0.1.0 public-ingress scope. Linux-native packaging and service installation are v0.1.1.

## 1. Release promise

A user can import a static website into the Android app, start hosting, open it locally, allow a second device on the same trusted Wi-Fi network to access it, and publish it through a custom domain using Cloudflare Tunnel or AssayFlux Relay over HTTP/HTTPS.

## 2. Required capabilities

- Main Android application and bundled Rust HTTP engine.
- HTML, CSS, browser JavaScript and static assets.
- Folder and ZIP import with staging and validation.
- ZIP archive maximum 500,000,000 bytes.
- Expanded website maximum 500,000,000 bytes.
- 5,000 files, 10,000 entries and 32 directory levels.
- <code>index.html</code> required at website root; ZIP may have one top-level containing folder.
- Local preview and configurable port.
- Loopback default and explicit opt-in LAN binding.
- Foreground hosting service, notification, start/stop/restart and recovery.
- Basic encrypted website backup/restore.
- Cloudflare Tunnel with encrypted token storage and external custom-domain HTTP/HTTPS verification.
- Standalone AssayFlux Relay Node installable on owner-controlled Termux/Android hardware; DNS TXT hostname proof, independent host credentials, authenticated outbound host session and visitor-only route. No paid VPS/Docker/PostgreSQL prerequisite; public IPv4/IPv6 reachability remains required.
- Minimal owner-facing Relay hostname setup/verification surface plus Termux/Android install, start/stop, status and restart/reconnect flow.
- Clear error, diagnostics and Android permission/lifecycle behavior.

## 3. Static response safety

The current Rust engine defines a 500,000,000-byte (500 MB decimal) per-file response cap. This is separate from the total website import quota. The 50 MiB limit applies to manual single-file import, not Rust serving. Text editing is capped at 256 KiB. If this cap changes, update Rust tests, resource budgets, security review, documentation and G0/G1/G3/G7/G8 evidence.

## 4. Technology boundaries

- React + TypeScript + Vite are the separate web-console operator client.
- React/Vite websites are supported in v0.1.0 as prebuilt static output, not as source builds on the phone.
- Main-app TypeScript compiler and Workbench build flow are v0.1.1.
- Python 3.11 and Node.js 24.22.1 are existing optional integrations in the separate runtime-worker APK; their acceptance is v0.1.1.
- Static hosting must continue when the runtime companion is absent or fails.

## 5. Current implementation evidence

The audited source includes:
- HostingService foreground service and recovery before starting the native engine.
- NativeEngine Rust HTTP listener with loopback default and LAN opt-in.
- SiteImporter folder/ZIP staging, path/name validation, quotas, index validation, activation and recovery.
- SiteBackupManager encrypted website archive/restore foundation.
- Websites & Files UI and site registry.
- Android integration workflow with compile-only and manually dispatched APK/emulator paths.

This is a source inventory, not proof that the first release passes on a physical device.

## 6. v0.1.0 exit

All ten G0–G9 gates must pass. The critical acceptance journey is folder/ZIP import, 500 MB boundary checks, local page load, second-device LAN load, Cloudflare and Relay public-domain HTTP/HTTPS from an independent network, assets/navigation, visitor/admin route isolation, stop/restart, Android lifecycle, backup/restore and artifact installation on a named physical phone.

## 7. Deferred work

Workbench preview, diff, TypeScript compilation, commits/deployment history, owner-approved publishing and rollback are v0.1.1. Python/Node acceptance is v0.1.1. Device trust, agents, full fleet control-plane acceptance and multi-host Relay scaling are v0.1.2.
