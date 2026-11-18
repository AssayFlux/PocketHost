# Pocket Host — Device Software and Role Matrix

**Roadmap placement:** v0.1.0 Android hosting; v0.1.1 optional runtime companion; v0.1.2 connected device fabric.

## Installable software products

| Target | Product | Current source status | Planned acceptance |
|---|---|---|---|
| Android phone/tablet | Pocket Host main APK | Kotlin/Compose app, Rust JNI hosting, site management, diagnostics and service foundations | v0.1.0 |
| Android runtime provider | Separate runtime-worker APK | Existing CPython 3.11 and Node.js 24.22.1 implementations, shipped together | v0.1.1 |
| Termux on Android | AssayFlux Relay Node | No standalone Termux package, installer or service exists in the audited source | v0.1.0 target |
| Linux home server / mini PC | AssayFlux Relay Node | No standalone Relay Node package exists; Linux-native package, installer and service lifecycle are deferred to v0.1.1 | v0.1.1 target |
| Browser | web-console | React/TypeScript/Vite operator console; Relay hostname panel source exists but is not a standalone gateway | v0.1.2 full console; minimal Relay setup in v0.1.0 |
| Linux x86_64 | Linux Node Agent | Preview Python package with SQLite jobs and HTTPS polling | v0.1.2 |
| Windows | Windows agent | Not present as a production package in audited tree | Later expansion |
| macOS | macOS agent | Not present as a production package in audited tree | Later expansion |
| Raspberry Pi/SBC | Board-specific agent/gateway | No certified board profile | Later expansion |
| iOS | Mobile client | No iOS source project in this repository | Later expansion |
| DemonCore/MCU | Separate firmware/board profiles | External project | Later adapter work |

## Mobile operating modes

| Mode | Website serving | Fleet administration |
|---|---|---|
| Server only | Enabled | Disabled |
| Command Center + Server | Enabled | Enabled when fleet implementation is delivered |

There is no Command Center-only mode. Both modes must serve websites. v0.1.0 accepts the hosting path; it does not certify the complete device fleet.

## Role compatibility

The planned worker roles are storage, traffic, build/publish preparation, native jobs, backup/recovery and edge gateway. A device is eligible only when its declared OS/architecture, isolation, resource budget and transport meet the role policy. Unknown capabilities are not treated as supported.

| Role | Permission boundary | Planned release |
|---|---|---|
| Mobile website server | Rust HTTP ingress and site registry | v0.1.0 |
| Storage | Named encrypted namespaces only | v0.1.2 |
| Backup/recovery | Encrypted snapshots and constrained restore staging | v0.1.2 |
| Traffic | Exact approved site routes and bounded proxy/cache | v0.1.2 |
| Build/publish | Scoped source input and immutable staging output; no signing/activation authority | v0.1.2 |
| Native job | Signed fixed-purpose operation; no arbitrary shell | v0.1.2 |
| Edge gateway | Board-profile allow-listed operations | Later expansion |
| Public Relay Node | Dedicated visitor-only HTTP gateway; no fleet admin authority | v0.1.0 |

## Web and runtime boundaries

- Main app v0.1.0: HTML, CSS, browser JavaScript and static assets served by Rust.
- React + TypeScript + Vite: separate browser operator console in web-console/.
- A Vite site can be served in v0.1.0 after an external production build; import the built output.
- Built-in website TypeScript compilation is v0.1.1.
- Python and Node are optional server-side runtimes in the separate runtime-worker APK. They are not required for static hosting.
- The future 20-language Runtime Library is not delivered by these three initial releases.

## Package and installer contract

Each OS/architecture product has a distinct artifact, signature, version, permissions and update/rollback path. The Relay Node is a separate installable product from the Android website-host APK and the full fleet control plane. A Termux gateway is a supported public endpoint only when its network has public IPv4/port forwarding or globally routed IPv6. Do not combine the Android host app, browser console and Linux agent into a single universal package. The Android host/companion signature-level IPC requirement must be preserved while the companion remains separate.

## Release acceptance

Use [RELEASE_GATES.md](RELEASE_GATES.md). A platform is supported only after its G4 artifact, G5 install/upgrade, G6 functional, G7 reliability and G8 security/UX evidence passes on named hardware/OS versions.
