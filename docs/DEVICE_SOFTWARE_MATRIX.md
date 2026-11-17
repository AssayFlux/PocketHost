# Pocket Host 0.1.0 — Device Software and Role Matrix

## Purpose

Pocket Host is a family of interoperable but separately installable products. Every product declares its OS, architecture, supported roles, permissions, protocol range and update path. Installing an agent never enrolls a device or grants a role.

## 1. Installable software products

| Device family | Product | Source-backed position | Remaining acceptance |
|---|---|---|---|
| Android phone/tablet | Pocket Host main APK | Kotlin/Compose app; website-serving foundation; persisted Server only / Command Center + Server modes; local trust workspace and control-plane client foundation | Hosting lifecycle on physical devices; full authenticated fleet integration; transport and recovery matrix |
| Android runtime provider | Separate runtime companion APK | Existing CPython 3.11 and Node.js mobile 24.22.1 integrations; both currently ship together | Android API/ABI acceptance, crash/restart, isolation/egress hardening and independent package split |
| Linux x86_64 | Pocket Host Linux Node Agent package | Preview package with P-256 identity, signed grants/jobs, durable SQLite queue, HTTPS poll/ack and allow-listed health/storage snapshot/backup executors | mTLS or stronger device transport, grant renewal/revocation, service resource isolation, signed updates, full mobile provisioning and physical acceptance |
| iPhone/iPad | Pocket Host iOS app | No iOS project or native source in this repository | Entire product implementation; preserve two-mode contract and only advertise server mode if platform lifecycle supports it |
| Windows x64/ARM64 | Pocket Host Node Agent installer/service | Not implemented in this repository | Native service, identity, role adapters, isolation, update/rollback and hardware acceptance |
| macOS Apple Silicon/Intel | Pocket Host Node Agent package/service | Not implemented in this repository | Native service, identity, sandbox controls, update/rollback and hardware acceptance |
| Raspberry Pi / SBC | Board/OS/architecture-specific agent or gateway | No certified board profile in this repository | Per-board package/image, resource and transport tests, edge integration |
| Embedded Linux gateway | Pocket Host Edge Agent | Planned | Protocol bridge, scoped device access, lifecycle and board acceptance |
| DemonCore-supported MCU | Separate DemonCore firmware and DCP adapter | DemonCore is an external AssayFlux project; Pocket Host does not vendor its firmware | Adapter implementation, board identity, secure provisioning and physical safety acceptance |

## 2. Mobile operating modes

| Mode | Website serving | Fleet command and approval |
|---|---|---|
| Server only | Enabled | Disabled |
| Command Center + Server | Enabled | Enabled |

There is no Command Center-only mode. The Android app currently contains the mode selector and a local device-trust foundation. Full fleet operation is not yet complete. iOS is planned, not implemented.

## 3. Role compatibility and current implementation

**Target** means an intended future role subject to platform acceptance. **Preview** means a limited implementation exists but is not production-supported. **Foundation** means source/UI exists but end-to-end acceptance remains. **No** means not part of the target role.

| Role | Android mobile app | Linux agent | Windows | macOS | SBC | MCU |
|---|---|---|---|---|---|---|
| Website Server | Foundation | No in 0.1.0 | No in 0.1.0 | No in 0.1.0 | No in 0.1.0 | No |
| Command Center (combined mode) | Foundation | No | No | No | No | No |
| Storage | Target | Preview: scoped local snapshot executor | Target | Target | Target | No |
| Traffic | Target | Not implemented | Target | Conditional | Conditional | No |
| Build/Publish | Target | Not implemented | Target | Target | Conditional | No |
| Native Backend/Jobs | Target | Preview: health-check job | Target | Target | Conditional | Bounded firmware tasks only |
| Backup/Recovery | Foundation: Android site/app backup | Preview: create/verify local snapshot | Target | Target | Target | No |
| Edge Gateway | Not implemented | Not implemented | Conditional | Conditional | Target | MCU endpoint only |

The Linux preview's storage/backup operations are local snapshot and verification primitives, not a complete distributed storage, restore or replication service. A role is not production-supported because its name appears in a protocol policy or UI.

## 4. Main-app web baseline and runtime add-ons

The main app includes HTML/CSS/JavaScript static hosting through the bundled Rust engine. TypeScript-to-browser-JavaScript compilation is a required built-in feature but the compiler is not yet bundled or verified. Basic static websites require no runtime companion.

The separate `runtime-worker` Android companion APK already integrates CPython 3.11 and Node.js mobile 24.22.1; both are **in progress**, shipped together in one companion APK, and are not required for static websites or Step 4. Independent per-language packages, Android device acceptance and security hardening remain open. The future Runtime Library is planned to split Python and Node into independent packages and add 18 more optional languages. The current catalog consists of manifests only, not downloadable runtime binaries. See [Future Runtime Library](FUTURE_RUNTIME_LIBRARY.md).

Python and Node currently share the companion application UID. The package boundary is not per-project OS isolation. Only trusted owner-controlled projects should execute until adversarial filesystem and egress tests pass.

React + TypeScript + Vite belong to the separate `web-console/` browser operator client. They are not dependencies of the main Android app. The main-app TypeScript compiler is a distinct website-authoring feature, not a server-side runtime.

Worker devices execute narrowly scoped native jobs and infrastructure adapters. They do not become arbitrary customer-language runtime hosts by default.

## 5. Transport status

| Transport | Intended use | Current repository position |
|---|---|---|
| Wi-Fi/LAN | Discovery, control, bulk transfer | Manual challenge/proof exchange exists; authenticated LAN discovery and mutual device transport remain open |
| Bluetooth LE | Proximity, pairing, small control messages | Planned; not an accepted end-to-end transport |
| Bluetooth Classic | Optional supported profiles | Planned/conditional; no acceptance evidence |
| USB cable | Provisioning, diagnostics, bulk transfer | Platform-specific future work; no generic iOS USB assumption |
| Remote network | Fleet commands and artifact exchange | Linux agent HTTPS bearer-token polling preview; this is not mutual TLS or full fleet acceptance |

Transport availability is distinct from authorization. Every transport must preserve the same device identity, signed role grant and revocation semantics.

## 6. Package and installer contract

Every production package/firmware release must include:
- Product/component version and source commit SHA.
- Target OS, version range, architecture and ABI/board.
- Supported role IDs and explicit permission inventory.
- Agent/host contract compatibility.
- Artifact SHA-256, signature identity and SBOM/license inventory.
- Installation, upgrade, uninstall, recovery and rollback instructions.
- Resource profile and known lifecycle limitations.
- Security contact and vulnerability reporting path.

Target installer behavior: first launch provisions a device identity; owner explicitly approves the device and exact role; uninstall/reinstall does not silently inherit trust; updates are signed, staged and rollback-capable. These are release requirements, not a claim that all current packages implement them.

## 7. Release acceptance

A product is releasable only after install/upgrade/revoke/uninstall and reboot tests on named target hardware, role-permission tests, network loss, resource pressure, signature verification and rollback. Planned and preview rows remain unsupported until evidence is published.
