# AssayFlux PocketHost

**Designed by AssayFlux** · Native Android website hosting

> **North Star:** Turn a capable Android phone into a simple, secure, observable and extensible website-hosting appliance—without requiring Termux.

PocketHost is a native Android application in development. It combines a Kotlin Android shell with a Rust hosting engine. Static hosting is the first workload. A TypeScript/React browser console is planned as a companion interface; Python and Node.js are optional future runtimes.

## Current implementation

The repository now contains an initial Phase 0 prototype:
- Kotlin + Jetpack Compose dashboard.
- Android foreground service for user-started hosting.
- JNI bridge to a Rust HTTP engine.
- Loopback-only static file serving with basic path validation.
- Bundled sample site.
- TypeScript + React + Vite console scaffold.
- Rust and console quality workflow.

**Important:** this is an early source implementation, not a verified APK. It has not yet been built or tested on the Vivo Y95. Cloudflare Tunnel, LAN/public access, site import/deployment, authentication, Python and Node.js are not implemented. Do not use this prototype as an internet-facing server.

## Architecture

| Technology | Responsibility |
|---|---|
| Rust | HTTP serving, static files and future routing/proxy engine |
| Kotlin | Native Android UI, service lifecycle and device integration |
| TypeScript | Optional browser console and web tooling |
| Python | Optional future backend runtime |
| Node.js | Optional future backend runtime |
| SQLite / Room | Planned metadata persistence |

## Build

See [Phase 0 status and prerequisites](docs/PHASE_0_STATUS.md). Android builds require JDK 17, Android SDK/NDK, Rust Android targets and `cargo-ndk`.

## Product direction

- [North Star](docs/NORTH_STAR.md)
- [Master Plan](docs/MASTER_PLAN.md)
- [Phase 0 implementation status](docs/PHASE_0_STATUS.md)

## License

Apache License 2.0. See [LICENSE](LICENSE).
