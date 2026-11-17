# Pocket Host 0.1.0 — Device Software and Role Matrix

## Purpose

Pocket Host is distributed as separate software for each device family. Every installer/firmware package declares its OS, architecture, supported roles, permissions, transports, protocol compatibility and update path. A generic package is never assumed to work on every device.

## 1. Installable software products

| Device family | Installable product | Primary purpose | 0.1.0 status |
|---|---|---|---|
| Android phone/tablet | Pocket Host Android APK | Website server in both modes; fleet command center is an optional additional capability in the combined mode | Existing app foundation; two-mode selection/enforcement, role separation and full fleet flows require implementation |
| iPhone/iPad | Pocket Host iOS app | Command center and owner approvals | Planned; native app not present in repository |
| Linux x86_64 (Python 3.11+) | Pocket Host Linux Node Agent package | Protocol identity, signed grant verification, durable assignment state, native health-check job | Preview foundation implemented; authenticated transport, service supervision, cgroups, role adapters and signed updates remain open |
| Windows x64/ARM64 where supported | Pocket Host Node Agent installer/service | Storage, traffic, build/publish, native jobs, backup | Planned |
| macOS Apple Silicon/Intel where supported | Pocket Host Node Agent package/service | Storage, traffic, build/publish, native jobs, backup | Planned |
| Raspberry Pi / SBC | Board/OS/architecture-specific Pocket Host Node Agent image/package | Compact worker, storage, traffic, build, edge gateway | Planned; each board profile needs acceptance |
| Embedded Linux gateway | Pocket Host Edge Agent package | Protocol bridge, bounded edge gateway, selected storage/traffic roles | Planned |
| DemonCore-supported MCU | Board-specific firmware and DCP adapter | Telemetry, sensors, actuators and bounded edge operations | DemonCore foundation exists; integration and board acceptance required |

## 2. Role compatibility matrix

Legend: **Target** means a planned role subject to hardware/OS acceptance; **Conditional** means only where the exact platform supports the role; **No** means the role is not part of this product baseline.

| Role | Android | iOS | Linux/server | Windows | macOS | SBC | MCU |
|---|---|---|---|---|---|---|---|
| Command Center (inside combined mode) | Target | Target | No | No | No | No | No |
| Mobile Website Server | Conditional | No promise | No in 0.1.0 | No in 0.1.0 | No in 0.1.0 | No in 0.1.0 | No |
| Storage | Conditional | No | Target | Target | Target | Target | No |
| Traffic | Conditional | No | Target | Conditional | Conditional | Conditional | No |
| Build/Publish | Conditional | No | Target | Target | Target | Conditional | No |
| Native Backend/Jobs | Conditional | No | Target | Target | Target | Conditional | Bounded firmware tasks only |
| Backup/Recovery | Conditional | No | Target | Target | Target | Target | No |
| Edge Gateway | No/limited | No | Target | Conditional | Conditional | Target | MCU endpoint only |

Role support is not granted by installation alone. A signed capability profile and physical/OS acceptance are required.

## 3. Main-app web baseline and future runtime add-ons

The Pocket Host main mobile app includes HTML/CSS static serving and the built-in TypeScript-to-browser-JavaScript build workflow. No extra runtime package or companion APK is required for basic websites.

The separate `runtime-worker` Android companion APK already integrates CPython 3.11 and Node.js mobile 24.22.1; both are **in progress** and are not required by Step 4. They currently ship together in the companion APK, not as two independent downloads. Independent per-language packages, Android device acceptance and security hardening remain open. The future Runtime Library adds 18 more optional languages, each as its own package. See [Future Runtime Library](FUTURE_RUNTIME_LIBRARY.md).

Worker devices execute Pocket Host's signed native node agent and narrowly defined job adapters. They may build source into immutable artifacts, store data, proxy approved traffic or run bounded infrastructure tasks. They do not become arbitrary customer-language runtime hosts by default.

## 4. Package requirements

Every package/firmware release includes:
- Product/component version and source commit SHA.
- Target OS, version range, architecture and ABI/board.
- Supported role IDs and explicit permission inventory.
- Agent/host contract compatibility.
- Artifact SHA-256, signature identity and SBOM/license inventory.
- Installation, upgrade, uninstall, recovery and rollback instructions.
- Resource profile and known lifecycle limitations.
- Security contact and vulnerability reporting path.

## 5. Installer behavior

- Installer does not silently enroll a device or assign a role.
- First launch generates/loads device identity and shows a one-time pairing challenge.
- Mobile owner approves the device and exact role grant.
- Uninstall/reinstall does not silently inherit trust; re-enrollment is required unless a secure migration flow explicitly transfers identity.
- Updates are signed, staged and rollback-capable.
- MCU firmware verifies board identity and signature before activation.

## 6. Transport support

| Transport | Intended use | Restrictions |
|---|---|---|
| Wi-Fi/LAN | Discovery, control, bulk transfer | Authenticated encrypted channel; discovery is untrusted |
| Bluetooth LE | Proximity, pairing, small control messages | Not a bulk transfer path or trust proof |
| Bluetooth Classic | Optional supported profiles | Exact OS/hardware/API validation |
| USB cable | Provisioning, diagnostics, bulk transfer | Only documented APIs and explicit permission; no generic iOS USB assumption |
| Remote network | Fleet commands and artifact exchange | Authenticated outbound connections, short-lived credentials and bounded queues |

## 7. Release acceptance

A product is releasable only after install/upgrade/revoke/uninstall and reboot tests on named target hardware, role-permission tests, network loss, resource pressure, signature verification and rollback. Planned rows remain unsupported until evidence is published.
