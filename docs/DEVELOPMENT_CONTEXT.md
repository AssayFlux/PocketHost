# Pocket Host — Engineering Context

This file is the contributor handover for the new release pathway.

## Active release

Work on **v0.1.0 — First Hosting** until the owner approves the next release. The former five-step sequence is retired. The three planned releases are v0.1.0 First Hosting, v0.1.1 Workbench, Runtime & Linux Relay Installation and v0.1.2 Connected Device Fabric.

## Product rules

- Mobile modes are exactly Server only and Command Center + Server; both include website serving.
- No standalone Command Center-only mode.
- Preserve Pocket Host Design Language V1. Do not redesign branding, login, account menu, hamburger navigation, typography, palettes or theme behavior as part of this roadmap.
- v0.1.0 is Android static website hosting with optional public access through Cloudflare Tunnel and AssayFlux Relay. Python/Node and Workbench are not dependencies.
- Folder/ZIP import: 500,000,000 bytes for compressed ZIP input and independently 500,000,000 bytes for expanded site contents; 5,000 files, 10,000 entries, 32 levels.
- Rust static response limit is 500,000,000 bytes (500 MB decimal) per file. Manual single-file import is 50 MiB; text editing is 256 KiB.
- React/Vite is the separate web-console app. Raw React/TypeScript source is not built by v0.1.0; import its built static output.
- Python 3.11 and Node.js 24.22.1 exist in the separate runtime-worker APK, currently together and in progress. Complete/accept them in v0.1.1.
- Workbench currently has a private draft/editor foundation. Complete preview/diffs/compiler/publishing/rollback in v0.1.1.
- Device trust, workers, full fleet control-plane acceptance and multi-host Relay scaling are v0.1.2 work. Cloudflare Tunnel and the native Android Relay service (Kotlin foreground service + Rust/Tokio JNI engine bundled in the main APK) are mandatory v0.1.0 work; Linux-native packaging is v0.1.1. No paid VPS is required, but public IPv4/IPv6 reachability is.
- Every version must pass G0–G9. See RELEASE_GATES.md.

## Repository boundaries

| Path | Responsibility |
|---|---|
| app/ | Android owner application and hosting lifecycle |
| rust-engine/ | Static HTTP engine and native Android library |
| runtime-worker/ | Optional Python/Node Android companion |
| web-console/ | Separate React/TypeScript/Vite browser client |
| agents/ | Platform-specific device agents |
| control-plane/ | Optional remote fleet coordination; current Relay gateway is coupled here and must be extracted |
| Relay Node (planned) | Native Kotlin foreground service + Rust/Tokio JNI engine bundled in the main APK; inbound visitor listener and node-side identity are not present yet; Linux-native packaging is v0.1.1 |
| protocol/ | Shared versioned device contracts |
| runtime-library/catalog/ | Future language catalog metadata |
| scripts/ | Build and validation helpers |
| docs/ | Product, release, architecture and acceptance documentation |

## Implementation rules

1. Read the relevant code and tests before changing behavior.
2. Reuse SiteRegistry, SiteImporter, backup and current UI foundations; avoid duplicate stores.
3. Keep ZIP compressed-size and expanded-size counters independent and overflow-safe.
4. Validate paths, entry counts, depth, filenames and file types before activation.
5. Keep staging separate from active website; preserve last-known-good on failure.
6. Keep website static serving independent of optional runtimes, public ingress and remote fleet control; each provider must fail independently.
7. Do not expand the Rust per-file response limit merely because total import increased.
8. Test UI, API, code and docs for every quota change.
9. Build APK artifacts explicitly; do not call compile-only a build.
10. Verify on named Android hardware and record exact OS, ABI, logs and artifact hash.
11. Update the gate ledger only with reproducible evidence.
12. Preserve Android system settings, safe areas, scrolling, keyboard insets, font scaling and permission flows.

## Current next actions

Close the remaining v0.1.0 hosting path: verify local and LAN serving, complete Cloudflare Tunnel and the native Android Relay Node service integrated into the Pocket Host APK, implement node-side host credential issuance and durable routing, verify the owner's domain over external HTTP/HTTPS, exercise host/gateway lifecycle and recovery, package the main APK with Relay JNI libraries, and complete physical-device/external-network acceptance. The Android Relay client already has independent credentials and lifecycle; the standalone gateway is still missing.
