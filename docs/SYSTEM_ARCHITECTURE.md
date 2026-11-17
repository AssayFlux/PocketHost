# Pocket Host 0.1.0 — System Architecture

**Product baseline:** 0.1.0  
**Primary owner interface:** Android or iPhone command-center application  
**Operating model:** mobile-controlled, role-assigned, interconnected device fleet  
**Core invariant:** device enrollment is explicit; every role is least-privilege; failures remain contained.

## 1. Product definition

Pocket Host lets an owner turn supported devices into a coordinated personal service system. The mobile application is the trusted command center. It can also be the website server, or perform both roles at once.

The user chooses one of three mobile modes:

| Mode | Mobile responsibilities | Website serving |
|---|---|---|
| Server | Serve websites from the mobile device and manage its own local services | On |
| Command Center + Server | Fleet command, device verification and local website hosting | On |

The command-center role and server role are independent switches. Turning off the mobile server role must not disable command-center access to other hosts. Closing the mobile application must not stop workloads that have been handed to autonomous host agents.

**Serving-node rule:** in 0.1.0, customer-selected language runtimes are installed on the mobile website-serving node only. When mobile Server mode is off, a website must be served by static/native capabilities or by an explicitly designated compatible serving arrangement; the system must not silently move customer runtimes onto worker devices. Runtime placement beyond the mobile serving node requires a separately approved architecture revision.

## 2. Mobile command center

Android and iOS are first-class command clients using the same versioned contracts and fleet model.

Responsibilities:
- Owner authentication, device discovery and explicit enrollment approval.
- Issue and revoke device verification challenges and role grants.
- Assign, change, pause and remove device jobs.
- Select website language runtimes and versions for the mobile serving node.
- Configure site releases, storage, traffic distribution, tunnels and backups.
- Observe health, capacity, connectivity, deployment and audit state.
- Approve sensitive actions, firmware updates and destructive operations.

The mobile app is not a fleet-wide single point of failure. Enrolled nodes retain their signed local assignments and continue already-authorized work during phone, Internet or control-plane outages. A replacement phone recovers owner authority through account recovery and device re-enrollment.

iOS is a first-class command center. iOS background execution restrictions mean arbitrary always-on hosting is not promised; Server mode is shown only where the OS and device capabilities actually support the required workload.

## 3. Device roles and assignments

A physical device may receive multiple compatible roles, but each role is an independent, revocable job with its own identity, permissions, resource budget, health and lifecycle.

| Role | Purpose | Typical devices |
|---|---|---|
| Command Center + Server | Owner approvals and fleet orchestration | Android/iPhone |
| Mobile Website Server | Final website ingress, static files and selected language runtimes | Supported Android phone/tablet |
| Storage Node | Encrypted object/file storage, snapshots and replication | PC, NAS, server, SBC |
| Traffic Node | Approved reverse proxy, cache, relay or traffic distribution | Linux server, SBC, eligible desktop |
| Build/Publish Worker | Build, test, package, sign and stage immutable releases | Desktop, laptop, server |
| Backend/Job Worker | Fixed-purpose native services, queues, scheduled jobs and approved adapters | Linux/Windows/macOS/server |
| Backup/Recovery Node | Encrypted backup copies and restore staging | NAS, server, SBC |
| Edge Gateway | Protocol translation and controlled MCU access | Raspberry Pi, embedded Linux |
| MCU/Device Node | Telemetry, sensor/actuator operations and bounded edge tasks | DemonCore, ESP32-class boards |

The user assigns jobs from the mobile command center. The scheduler checks signed capabilities, OS support, available capacity, power/thermal state, trust level, network reachability and required data locality. It must refuse unsupported assignments rather than guess.

A device may be storage-only, build-only or traffic-only. It does not become a general-purpose runtime host merely because it is enrolled.

## 4. System topology

```
                   OWNER
                    |
        Android / iPhone COMMAND CENTER
          | Command Center + Server mode
          | optional Mobile Server role
          | language runtimes only here
          |
    enrollment + signed role assignments
          |
    +-----+-----------+-----------+----------------+
    |                 |           |                |
 Storage node    Traffic node  Build/publish   Backend/job
    |                 |           |             worker
    +-----------------+-----------+----------------+
                      |
              authenticated data plane
                      |
        Mobile website server / site ingress
                      |
            site router and runtime adapters
                      |
       static + Python + Node + selected runtimes
                      |
        Local / AssayFlux Relay / Cloudflare
                      |
                   Visitors

 Raspberry Pi / edge gateway -> DemonCore / MCU devices
```

Traffic workers can proxy or cache approved traffic, but the final website ingress and customer runtime execution remain on the designated mobile serving node in this baseline. A traffic node cannot gain access to runtime control, owner management or arbitrary host ports.

## 5. Device integration and trust

All integrations are initiated or explicitly approved by the mobile command center.

### Supported transport choices

- **Wi-Fi / LAN:** preferred for discovery, control and bulk transfer where the network is trusted. Use mutually authenticated encrypted sessions.
- **Bluetooth LE:** discovery, proximity checks, short pairing exchanges and small control messages. It is not a bulk storage/build transport.
- **Bluetooth Classic:** optional where both platforms and device APIs support the required profile.
- **USB data cable:** direct provisioning, diagnostics and bulk transfer only through supported Android USB host/accessory or platform-approved interfaces. Do not assume generic USB control on iOS.
- **Remote network:** authenticated outbound connections and the optional control plane/relay when devices are not on the same LAN.

Transport discovery is not trust. A device name, MAC address, IP address, QR code or Bluetooth visibility does not authorize integration.

### Enrollment ceremony

1. Candidate device installs the matching Pocket Host node software.
2. Candidate creates a hardware-backed key where supported and displays a short-lived device ID/fingerprint and one-time verification code.
3. Mobile command center scans/discovers the candidate and verifies the challenge over the candidate's authenticated channel.
4. Owner confirms the device identity, displayed capabilities, requested role and permissions.
5. The command center approves the exact role grant and issues a device-bound, scoped, revocable credential.
6. Candidate records the grant and reports its accepted role and policy.
7. The owner can inspect, pause, change or revoke that grant at any time.

Verification codes are short-lived, single-use pairing proofs, not permanent passwords. Device identity is bound to a cryptographic key and registry record. A copied code cannot enroll another device after expiry or use.

Unknown, unapproved or revoked devices cannot join the trusted data plane, receive jobs, access storage or proxy traffic.

## 6. Communication and job contracts

All devices implement a versioned host-agent contract. Commands are typed, allow-listed, scoped, expiring, idempotent and audited. No arbitrary shell, unrestricted filesystem, generic process execution or open-ended remote script command is exposed.

Each job includes:
- job and assignment IDs, target device ID and owner/team;
- role, requested capabilities and explicit permission set;
- artifact digest and signed publisher identity where applicable;
- resource, storage, network, power and time budgets;
- input/output data locations and retention rules;
- health/readiness checks, retry policy and rollback behavior;
- issued/expiry times, idempotency key and audit correlation.

The device validates every job locally before accepting it. It persists accepted assignment state and reports desired versus observed state. Delivery is at-least-once; operations must be idempotent. Unknown or incompatible contract versions are rejected safely.

## 7. Website serving and runtime model

The mobile website server is the final serving node in this baseline:
- Rust ingress and static website delivery are foundational.
- Python, Node and other supported language runtimes are separately selectable installable runtime packs.
- User installs only the runtimes needed by the website.
- A website can use several installed runtimes, but each project/component receives a distinct OS-enforced execution identity and isolated data/capabilities.
- Runtime processes are supervised independently; static delivery remains available if a language runtime fails.
- Worker devices may build, validate, stage, cache, store or distribute immutable artifacts, but do not execute customer-selected language runtimes in 0.1.0.
- Publishing is a staged, verified, atomic activation on the mobile serving node. A build worker cannot publish directly without owner-authorized approval and serving-node verification.

Runtime packs must declare language/version, ABI, platform compatibility, dependencies, license, digest, signature, health check, resource limits and rollback path. No runtime is installed automatically without owner consent.

## 8. Data and traffic roles

Storage nodes hold only explicitly assigned encrypted datasets or object namespaces. Keys are scoped and not shared with unrelated roles. Replication and backup are distinct from the live authoritative serving copy.

Traffic nodes receive only explicit site/hostname routes and bounded proxy/cache capabilities. They do not terminate into management APIs, runtime brokers, device administration or MCU control. Cache policy must respect private/no-store content and owner configuration.

Build/publish nodes receive source and build inputs only for the authorized job. Signing keys remain in protected signing infrastructure or approved secure device storage; a generic worker must never receive owner identity keys.

Backend/job workers in this baseline execute approved native worker packages and bounded job adapters. They do not become arbitrary Python/Node hosting nodes. This preserves the user's central runtime placement rule while allowing useful distributed support work.

## 9. DemonCore and microcontrollers

Raspberry Pi and other capable SBCs can run an edge gateway. DemonCore and supported MCU nodes connect through a board-aware adapter with:
- per-board identity and firmware version;
- signed firmware and verified OTA updates;
- allow-listed sensor, actuator and GPIO capabilities;
- explicit safe-state and physical safety policy;
- rate, duty-cycle and timeout limits;
- audit and revocation.

MCUs are not assumed to run general-purpose website runtimes. A gateway failure must not stop the mobile website server or unrelated nodes.

## 10. Connectivity providers

Local Network, first-party AssayFlux Relay and Cloudflare Tunnel remain independent visitor connectivity providers. Users may enable either provider or both. Each has independent credentials, hostnames, route health and revocation. Provider failure does not stop local serving or another provider.

All public routes terminate at the approved website ingress. Traffic worker routing is separately granted. Management and device-control endpoints are never visitor routes.

## 11. Fault domains and autonomy

- Command-center app failure does not stop accepted jobs on enrolled nodes.
- Mobile Server role can stop without disabling the command-center role.
- One worker failure does not stop other workers.
- Storage, traffic, build and backend jobs have independent queues and credentials.
- A runtime crash does not stop static serving or other runtimes.
- A build failure does not alter the active website release.
- A tunnel failure does not stop local hosting or another tunnel.
- A device revocation removes its access without revoking unrelated devices.
- Telemetry and control-plane outages do not block local service request paths.
- The host agent applies resource limits, backpressure and bounded retries locally.

## 12. Compatibility and honest support

Every platform has a separate installable product:
- Android: mobile command center and optional mobile host/worker agent.
- iOS: mobile command center; hosting only if a supported OS capability and acceptance test permit it.
- Linux, Windows and macOS: native host/worker agent installers.
- Raspberry Pi/SBC: board/OS/architecture-specific host or gateway image/package.
- MCU: firmware and board-specific DemonCore adapter.

A platform is supported only for tested roles, transports, OS versions and hardware. Unsupported USB, Bluetooth, background execution, filesystem, isolation or firmware features are not advertised.

## 13. Acceptance invariants

- Owner approves exact device identity and role before it joins the trusted fleet.
- Expired/replayed verification codes and revoked identities are rejected.
- The two mobile modes can be switched without corrupting accepted worker jobs. Server only cannot issue fleet commands or approve integrations; only Command Center + Server can.
- Phone/app/control-plane loss leaves authorized jobs running according to local policy.
- A storage-only node cannot execute jobs or proxy traffic.
- A traffic-only node cannot access management or runtime broker endpoints.
- Build workers cannot activate a release without serving-node verification and authorized deployment.
- Customer-selected language runtimes execute only on the designated mobile serving node in this baseline.
- One worker, runtime, storage provider, tunnel or MCU failure remains isolated.
- Every job, role change, credential issue/revocation and release activation is auditable.
