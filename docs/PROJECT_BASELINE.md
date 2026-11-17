# Pocket Host 0.1.0 — Repository Baseline

## Product identity

- Product: AssayFlux Pocket Host
- Version: 0.1.0
- Canonical branch: main
- Primary owner interface: Android or iPhone command center
- Mobile modes: Server only; Command Center + Server
- System form: verified, role-assigned device fleet

## Repository implementation inventory

| Area | Existing foundation | 0.1.0 role |
|---|---|---|
| Android app | Kotlin, Jetpack Compose, AndroidX | Command center and optional mobile host |
| Android runtime companion | Separate Android package; Python/Node integration | Language runtime provider on mobile serving node |
| Rust engine | Tokio/Hyper native library | Mobile website ingress and routing |
| Remote management API | Android loopback API | Local mobile management |
| Remote control agent | Android agent polling configured control plane | Host connection foundation |
| Control plane | Rust/Axum, PostgreSQL, migrations, commands and audit | Optional remote fleet coordination |
| Browser console | React/TypeScript/Vite | Secondary operator client |
| Cloudflare connector | cloudflared Android integration | Optional visitor ingress |
| AssayFlux Relay | Rust visitor gateway and Android agent source | First-party bounded HTTP visitor ingress |
| DemonCore | Separate ESP32 MicroPython firmware, DCP and board profiles | MCU/edge foundation |

## Platform and role coverage

- Android: application and mobile host foundation present; role model, verification ceremony and fleet worker roles require implementation/acceptance.
- iOS: no native application workspace in this repository; command-center app is a planned deliverable.
- Linux/Windows/macOS: no production host-agent installer is claimed.
- Raspberry Pi/SBC: no named production board/OS profile is accepted.
- MCU: DemonCore firmware/protocol foundation exists; each board, secure enrollment and capability needs physical acceptance.

## Current implementation boundaries

The Android UI, local management API, remote-control agent and tunnel lifecycle are coordinated in the Android app package/process; the Rust engine is a native library hosted by the app. The runtime companion is a separate package. Python and Node projects share its application UID, so this package boundary alone is not per-project isolation. The control plane is separately deployable and the browser console is a separate client.

The repository contains useful foundations, not yet the full device-fabric implementation. The following must not be inferred from the architecture documents:
- verified multi-device pairing and role grants are not complete merely because device IDs or QR flows exist;
- Bluetooth and USB transports are not supported until their platform-specific implementations and tests exist;
- Linux, Windows, macOS and SBC installers are not complete;
- the iOS app is not present;
- storage, traffic, build/publish and backend worker roles are not production-ready merely because the role model is specified;
- central runtime placement and per-project isolation require implementation and adversarial tests.

## Normative specifications

- [System Architecture](SYSTEM_ARCHITECTURE.md)
- [Master Plan](MASTER_PLAN.md)
- [Host Agent and Role Contract](HOST_AGENT_CONTRACT.md)
- [Device Software Matrix](DEVICE_SOFTWARE_MATRIX.md)
- [Roadmap](ROADMAP.md)
- [UI Design](UI_DESIGN.md)
- [Security Isolation](SECURITY_ISOLATION_DESIGN.md)
- [Remote Control Acceptance](REMOTE_CONTROL_ACCEPTANCE.md)
