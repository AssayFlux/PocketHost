# Pocket Host 0.1.0 — Baseline

## Product identity

- Product: AssayFlux Pocket Host
- Version: 0.1.0
- Canonical branch: main
- Primary human interface: Android and iOS mobile command-center applications
- System form: distributed self-hosting ecosystem
- Core principle: independent components connected through authenticated contracts

## Repository implementation inventory

| Area | Current repository foundation | Product role |
|---|---|---|
| Android app | Kotlin, Jetpack Compose, AndroidX | Mobile command center and Android host client |
| Android runtime companion | Separate Android package; Python/Node runtime integration | Runtime provider boundary |
| Rust engine | Tokio/Hyper native library | Android website ingress and routing |
| Remote management API | Android loopback API | Local owner management |
| Remote control agent | Android agent polling configured control plane | Remote host connection |
| Control plane | Rust/Axum, PostgreSQL, migrations, device commands, audit | Optional remote fleet coordination |
| Browser console | React/TypeScript/Vite | Secondary operator client |
| Cloudflare connector | cloudflared Android integration | Optional visitor ingress |
| AssayFlux Relay | Rust visitor gateway and Android agent source | First-party bounded HTTP visitor ingress |
| Tests/CI | Android instrumentation, Rust tests, Python/Node tests, console build, workflow validation | Engineering verification |
| DemonCore | ESP32 MicroPython firmware, DCP, board profile and simulator | Separate MCU/edge-device project |

## Version baseline

The Android app, runtime companion, Rust website engine, control plane and browser console declare 0.1.0 in their current build metadata. The product release identity is 0.1.0. A common release manifest and cross-component compatibility matrix are required before a coordinated multi-platform distribution.

## Platform coverage

- Android: application and host implementation present.
- iOS: no native application workspace in this repository; must be implemented as a first-class mobile control client.
- Linux/Windows/macOS: no host agent implementation is claimed by this baseline.
- Raspberry Pi/SBC: no named board profile is accepted by this baseline.
- MCU: DemonCore firmware/protocol foundation exists; each board and secure fleet capability requires physical acceptance.

## Component separation assessment

The repository contains logical modules, but not every module is yet an independent operating-system fault domain. The Android UI, local management API, remote-control agent and tunnel lifecycle are coordinated within the Android application package/process; the Rust engine is a native library hosted by that app. The runtime companion is a separate package, but Python and Node projects share its application UID. The remote control plane is separately deployable, and the browser console is a separate client. These boundaries are implementation facts, not proof that all desired isolation and failure-containment guarantees already pass.

The 0.1.0 architecture requires independent supervisors, process identities, resource budgets, health and rollback for components according to their platform capabilities. The mobile command center must not be required for workloads to continue. Platform, runtime, security, tunnel, fleet and device support must be claimed only against named target tests and release evidence.

## Normative specifications

- [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md)
- [MASTER_PLAN.md](MASTER_PLAN.md)
- [ROADMAP.md](ROADMAP.md)
- [UI_DESIGN.md](UI_DESIGN.md)
- [SECURITY_ISOLATION_DESIGN.md](SECURITY_ISOLATION_DESIGN.md)
- [REMOTE_CONTROL_ACCEPTANCE.md](REMOTE_CONTROL_ACCEPTANCE.md)
