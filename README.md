# AssayFlux PocketHost

**Designed by AssayFlux** · Android-native personal website hosting

> **North Star:** Turn a capable Android phone into a simple, secure, observable and extensible website-hosting appliance—without requiring Termux or a rented traditional web host.

PocketHost is planned as a native Android application that lets its owner import, deploy and operate websites from the phone. Static hosting is the first product capability. Optional Python and Node.js application runtimes follow only after the Android networking and resource constraints are proven.

## Product direction

- **Native Android app:** Kotlin + Jetpack Compose.
- **Central hosting engine:** Rust, designed as a reusable engine independent of Android UI.
- **Web management console:** React + TypeScript (optional companion interface, not a replacement for the native app).
- **Optional application runtimes:** Python and Android-compatible Node.js.
- **Public connectivity:** Cloudflare Tunnel, subject to an Android ARM64 feasibility proof.
- **Local persistence:** SQLite/Room; Android Keystore-backed protection for secrets.
- **Initial experience:** Simple Mode, with Advanced Mode for operators who need detailed controls.

## Current status

This repository currently contains the product vision and engineering plan. It is **not yet a working hosting application**. No claim is made that Cloudflare Tunnel, Python, or Node.js has been validated on the target phone.

The first milestone is a feasibility prototype: compile and load a Rust Android library, control it from Kotlin, serve a local static website, and test an Android-compatible Cloudflare connector.

## Documentation

- [North Star](docs/NORTH_STAR.md) — product purpose, principles and success definition.
- [Master Plan](docs/MASTER_PLAN.md) — architecture, UX, security, roadmap and acceptance criteria.

## Initial technology boundaries

| Technology | Owns |
|---|---|
| Rust | HTTP serving, routing, proxying, request policies, runtime supervision and metrics |
| Kotlin | Android UI, lifecycle, permissions, foreground service, notifications and device integration |
| TypeScript | Optional browser console, typed API client, build tooling and shared management contracts |
| Python | Optional user-selected Python backend applications |
| Node.js | Optional user-selected Node applications, only with a validated Android runtime |
| SQLite / Room | Site metadata, deployment history and local configuration |

TypeScript is authored as `.ts` / `.tsx` and compiled to JavaScript for browser or Node execution. It does not replace Kotlin on Android or Rust in the core engine.

## Device-first constraints

The original target is a Vivo Y95-class device. Exact model, Android version, ABI and available memory must be confirmed before fixing build targets. Static hosting is prioritized; Python and Node runtimes remain optional and must be loaded only when required. Android foreground services improve continuity but cannot guarantee server-grade 24/7 uptime.

## Development sequence

1. Prove Rust + Kotlin/JNI + local HTTP on the target device.
2. Build the native app foundation and service lifecycle.
3. Implement secure static hosting and website deployment.
4. Prove Cloudflare Tunnel on Android before promising public hosting.
5. Add management console, then optional Python and Node runtimes.
6. Complete security, recovery, device testing and release engineering.

See [docs/MASTER_PLAN.md](docs/MASTER_PLAN.md) for detailed scope and gates.

## License

Apache License 2.0. See [LICENSE](LICENSE).
