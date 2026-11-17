# Pocket Host 0.1.0 — Repository Baseline

## Product identity

- Product: AssayFlux Pocket Host
- Version: 0.1.0
- Canonical branch: main
- Primary owner interface: Android or iPhone command center
- Mobile modes: Server only; Command Center + Server. Both serve websites; only the combined mode exposes fleet command, device verification and role assignment.
- System form: verified, role-assigned device fleet

## Repository implementation inventory

| Area | Existing foundation | 0.1.0 role |
|---|---|---|
| Android app | Kotlin, Jetpack Compose, AndroidX | Website server in both modes; fleet command center in combined mode |
| Android runtime companion | Separate Android APK; CPython 3.11 via Chaquopy and Node.js mobile 24.22.1 via JNI | Existing implementations in progress; Android device acceptance and independent per-language package distribution remain open; not required by main app or Step 4 |
| Rust engine | Tokio/Hyper native library | Mobile website ingress and routing |
| Remote management API | Android loopback API | Local mobile management |
| Remote control agent | Android agent polling configured control plane | Host connection foundation |
| Control plane | Rust/Axum, PostgreSQL, migrations, commands and audit | Optional remote fleet coordination |
| Browser console | React/TypeScript/Vite | Secondary operator client |
| Cloudflare connector | cloudflared Android integration | Optional visitor ingress |
| AssayFlux Relay | Rust visitor gateway and Android agent source | First-party bounded HTTP visitor ingress |
| DemonCore | Separate ESP32 MicroPython firmware, DCP and board profiles | MCU/edge foundation |

## Platform and role coverage

- Android: website-serving foundation and persisted Server only / Command Center + Server selector are present. Combined mode exposes a local device-trust workspace with Android Keystore P-256 identity, signed five-minute challenge, proof-of-possession, role-allow-list-checked grant, encrypted registry and revocation. Authenticated transports, remote control-plane integration and independently installed agent acceptance remain open.
- iOS: no native application workspace in this repository; command-center app is a planned deliverable.
- Linux/Windows/macOS: no production host-agent installer is claimed.
- Raspberry Pi/SBC: no named production board/OS profile is accepted.
- MCU: DemonCore firmware/protocol foundation exists; each board, secure enrollment and capability needs physical acceptance.

## Current implementation boundaries

The Android UI, local management API, remote-control agent and tunnel lifecycle are coordinated in the main Android app package/process; the Rust engine is a native library hosted by the app. The separate runtime companion APK already integrates CPython 3.11 and Node.js mobile 24.22.1. These integrations are in progress; both runtimes share the companion application UID, so the package boundary alone is not per-project isolation. They are not part of Step 4 or required for the main app's static web server. The control plane is separately deployable and the browser console is a separate client.

The repository contains useful foundations, not yet the full device-fabric implementation. The following must not be inferred from the architecture documents:
- end-to-end multi-device pairing and worker-consumed role grants are not complete; the Android implementation is a manual local trust-authority foundation and is not yet connected to the control plane or independent agents;
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
- [Step 2 Device Trust Implementation](STEP_2_DEVICE_TRUST_IMPLEMENTATION.md)
