# Pocket Host 0.1.0 — Device Software and Role Matrix

## Purpose

Pocket Host is distributed as separate software for each device family. Every installer/firmware package declares its OS, architecture, supported roles, permissions, transports, protocol compatibility and update path. A generic package is never assumed to work on every device.

## 1. Installable software products

| Device family | Installable product | Primary purpose | 0.1.0 status |
|---|---|---|---|
| Android phone/tablet | Pocket Host Android APK | Command center; optional mobile website server; eligible local worker | Existing app foundation; role separation and full fleet flows require implementation |
| iPhone/iPad | Pocket Host iOS app | Command center and owner approvals | Planned; native app not present in repository |
| Linux x86_64/ARM64 | Pocket Host Node Agent package/service | Storage, traffic, build/publish, native jobs, backup, gateway where compatible | Planned |
| Windows x64/ARM64 where supported | Pocket Host Node Agent installer/service | Storage, traffic, build/publish, native jobs, backup | Planned |
| macOS Apple Silicon/Intel where supported | Pocket Host Node Agent package/service | Storage, traffic, build/publish, native jobs, backup | Planned |
| Raspberry Pi / SBC | Board/OS/architecture-specific Pocket Host Node Agent image/package | Compact worker, storage, traffic, build, edge gateway | Planned; each board profile needs acceptance |
| Embedded Linux gateway | Pocket Host Edge Agent package | Protocol bridge, bounded edge gateway, selected storage/traffic roles | Planned |
| DemonCore-supported MCU | Board-specific firmware and DCP adapter | Telemetry, sensors, actuators and bounded edge operations | DemonCore foundation exists; integration and board acceptance required |

## 2. Role compatibility matrix

Legend: **Target** means a planned role subject to hardware/OS acceptance; **Conditional** means only where the exact platform supports the role; **No** means the role is not part of this product baseline.

| Role | Android | iOS | Linux/server | Windows | macOS | SBC | MCU |
|---|---|---|---|---|---|---|---|
| Command Center | Target | Target | No | No | No | No | No |
| Mobile Website Server | Conditional | No promise | No in 0.1.0 | No in 0.1.0 | No in 0.1.0 | No in 0.1.0 | No |
| Storage | Conditional | No | Target | Target | Target | Target | No |
| Traffic | Conditional | No | Target | Conditional | Conditional | Conditional | No |
| Build/Publish | Conditional | No | Target | Target | Target | Conditional | No |
| Native Backend/Jobs | Conditional | No | Target | Target | Target | Conditional | Bounded firmware tasks only |
| Backup/Recovery | Conditional | No | Target | Target | Target | Target | No |
| Edge Gateway | No/limited | No | Target | Conditional | Conditional | Target | MCU endpoint only |

Role support is not granted by installation alone. A signed capability profile and physical/OS acceptance are required.

## 3. Mobile runtime placement

In 0.1.0, customer-selected language runtimes are installed and executed only on the designated mobile website-serving node. The Android runtime companion is the initial implementation provider. Each language is a separate signed/versioned pack and can be installed or removed independently.

Worker devices execute Pocket Host's signed native node agent and narrowly defined job adapters. They may build source into immutable artifacts, store data, proxy approved traffic or run bounded infrastructure tasks. They do not host arbitrary customer-selected Python/Node/PHP projects in this baseline.

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
