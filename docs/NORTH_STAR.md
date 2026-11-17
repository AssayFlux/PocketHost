# Pocket Host 0.1.0 — Product North Star

## Purpose

Make personal and community-owned infrastructure approachable. A user controls a verified fleet of capable devices from an Android command center (iOS is a future target, not yet in this repository), assigns each device a useful job and combines their capabilities without making the fleet fragile.

## Product promises

- Mobile is the primary command center.
- Mobile has exactly two operating modes: Server only, or Command Center + Server.
- Every external device is verified and explicitly approved before integration.
- Every device runs platform-specific software/firmware for its supported role.
- Users assign devices to storage, traffic, build/publish, native backend jobs, backup or edge gateway roles.
- In 0.1.0, user-selected language runtimes execute only on the mobile website-serving node.
- Static hosting does not depend on optional language runtimes.
- Devices communicate through authenticated, encrypted, versioned and bounded contracts.
- Wi-Fi/LAN is preferred for bulk communication; BLE supports discovery and small control messages; USB is platform/API-specific; remote links are authenticated.
- A device or role failure does not stop unrelated services.
- Local Network, AssayFlux Relay and Cloudflare Tunnel are independent visitor connectivity choices.
- DemonCore provides MCU firmware and bounded edge-device capabilities through an authenticated adapter.

## Mobile operating model

| Mode | Command role | Website server role |
|---|---|---|
| Server only | Off | On |
| Command Center + Server | On | On |

Server only exposes website-serving controls and does not enroll devices or assign fleet roles. Command Center + Server exposes both fleet administration and website-serving controls. The mobile app is not required for already-authorized jobs on other devices to continue. iOS is a first-class command client; always-on hosting is not promised where Apple's platform lifecycle does not support it.

## Device fabric

The fleet may include Android devices, iPhones as command clients, Linux/Windows/macOS computers, servers, VPSs, Raspberry Pi and other SBCs, embedded Linux gateways, DemonCore boards, ESP32-class MCUs and constrained sensors.

A device receives only roles supported by its measured OS, architecture, hardware, resource, transport and security capabilities. Discovery is not trust. The owner approves a cryptographic device identity, one-time challenge and exact role grant.

## Runtime placement

The 0.1.0 serving model centralizes customer-selected language runtimes on the mobile website-serving node. User installs only the languages required by the website. Worker nodes provide infrastructure jobs, storage, traffic proxy/cache, builds, publishing preparation, backups and fixed-purpose native services. They do not silently execute customer-selected language projects.

## Interconnection and resilience

The control plane coordinates identity, enrollment, role grants, desired state, job leases and audit. The data plane carries website traffic, storage transfer, build artifacts, worker jobs and edge telemetry. Each device retains accepted local assignments. Control-plane, mobile, tunnel, storage, worker, runtime and MCU failures have bounded effects.

## Security invariants

- Device key, owner identity, mobile client, role grant, job, runtime and MCU identity are distinct.
- One-time codes are short-lived proofs, never permanent credentials.
- No arbitrary remote shell or unrestricted filesystem access.
- Visitor ingress never reaches management, runtime broker or MCU controls.
- Build workers cannot activate a live release directly.
- Storage and traffic roles receive only named namespaces/routes.
- MCU actions are board-verified, allow-listed and safety-bounded.

## Success definition

A user installs the correct node software, verifies it from a phone, assigns it a job and sees it operate independently. The mobile device can serve a website, manage the fleet, or do both. A broken node, runtime, tunnel, job or edge device does not take down unrelated parts of the system.
